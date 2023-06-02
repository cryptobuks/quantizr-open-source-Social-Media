
package quanta.lucene;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.PreDestroy;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import quanta.config.AppProp;
import quanta.config.ServiceBase;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.StreamUtil;
import quanta.util.XString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// other compresison input types (not currently supported, but trvial to add, as
// a 'CompressionType'):
// org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
// org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
// org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
// org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
/**
 * Recursively scans all files in a folder (and subfolders) and indexes them into Lucene.
 * 
 * todo-2: - need to do threadsafety in this class. That's not considered yet. - make multiple
 * simultaneous searches work ok (no shared vars) - make an attemp to search while indexing is
 * underway get blocked (with error message saying why) * actually doublecheck this, and see if the
 * index SUPPORTS reads to it DURING indexing process.
 * 
 * todo-2: for any inputstreams that are not wrapped in a BufferedInputStream I need to do that.
 */
@Component
public class FileIndexer extends ServiceBase {
	
	private static Logger log = LoggerFactory.getLogger(FileIndexer.class);
	@Autowired
	private AppProp appProp;
	private IndexWriter writer;
	private FSDirectory fsDir;
	private int filesAdded = 0;
	private int filesUpdated = 0;
	private int filesSkipped = 0;
	private boolean initialized = false;
	private HashSet<String> suffixSet = new HashSet<>();


	private enum CompressionType {
		NONE, GZIP, XZIP;
	}

	public void index(String dirToIndex, String luceneIndexDataSubDir, String suffixes, boolean forceRebuild) {
		init(forceRebuild, luceneIndexDataSubDir);
		buildSuffixSet(suffixes);
		final long now = System.currentTimeMillis();
		log.info("Indexing directory: " + dirToIndex);
		// legacy code left intact but disabled here
		// indexDirectory(new File(dirToIndex), suffix);
		try {
			indexDocs(Paths.get(dirToIndex));
			log.debug("Index docs complete.");
		} catch (Exception e) {
			log.error("failed to index documents.", e);
		}
		log.info("Indexing completed in {} milliseconds.", System.currentTimeMillis() - now);
	}

	private boolean isZipFileFormatFileName(String fileName) {
		fileName = fileName.toLowerCase();
		return fileName.endsWith(".zip") || fileName.endsWith(".jar");
	}

	private boolean isTarFileFormatFileName(String fileName) {
		fileName = fileName.toLowerCase();
		return fileName.endsWith(".tar");
	}

	private boolean isTgzFileFormatFileName(String fileName) {
		fileName = fileName.toLowerCase();
		return fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz");
	}

	private boolean isTxzFileFormatFileName(String fileName) {
		fileName = fileName.toLowerCase();
		return fileName.endsWith(".txz") || fileName.endsWith(".tar.xz");
	}

	/*
	 * Takes a comma delimited list of suffixes, and loads up the suffixSet for faster access
	 */
	private void buildSuffixSet(String suffixes) {
		suffixSet = XString.tokenizeToSet(suffixes.toLowerCase(), ",", true);
	}

	private void init(boolean forceRebuild, String luceneIndexDataSubDir) {
		if (initialized) return;
		initialized = true;
		if (StringUtils.isEmpty(appProp.getLuceneDir())) {
			throw ExUtil.wrapEx("Lucend Data Dir is not configured.");
		}
		try {
			fsDir = FSDirectory.open(Paths.get(appProp.getLuceneDir() + "/" + luceneIndexDataSubDir));
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			if (forceRebuild) {
				// Create a new index in the directory, removing any
				// previously indexed documents:
				iwc.setOpenMode(OpenMode.CREATE);
			} else {
				// Add new documents to an existing index:
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
			}
			/*
			 * Optional: for better indexing performance, if you are indexing many documents, increase the RAM
			 * buffer. But if you do this, increase the max heap size to the JVM (eg add -Xmx512m or -Xmx1g):
			 * 
			 * iwc.setRAMBufferSizeMB(256.0);
			 */
			writer = new IndexWriter(fsDir, iwc);
		} catch (IOException e) {
			throw ExUtil.wrapEx(e);
		}
	}

	/**
	 * Indexes the given file using the given writer, or if a directory is given, recurses over files
	 * and directories found under the given directory.
	 * 
	 * NOTE: This method indexes one document per input file. This is slow. For good throughput, put
	 * multiple documents into your input file(s). An example of this is in the benchmark module, which
	 * can create "line doc" files, one document per line, using the <a href=
	 * "../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
	 * >WriteLineDocTask</a>.
	 */
	private void indexDocs(Path path) throws Exception {
		filesAdded = 0;
		filesUpdated = 0;
		filesSkipped = 0;
		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					try {
						String absPath = file.toString();
						String ext = FilenameUtils.getExtension(absPath);
						if (!suffixSet.contains(ext)) {
							filesSkipped++;
							return FileVisitResult.CONTINUE;
						} else if (isZipFileFormatFileName(absPath)) {
							indexZipFile(file, absPath);
						} else if (isTarFileFormatFileName(absPath)) {
							indexTarFile(file, absPath, CompressionType.NONE);
						} else if (isTgzFileFormatFileName(absPath)) {
							indexTarFile(file, absPath, CompressionType.GZIP);
						} else if (isTxzFileFormatFileName(absPath)) {
							indexTarFile(file, absPath, CompressionType.XZIP);
						} else {
							indexDoc(file, attrs.lastModifiedTime().toMillis());
						}
					} catch (Exception ignore) {
						// don't index files that can't be read.
						log.debug("Unable to index file: " + file.getFileName().toString());
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			String absPath = path.toString();
			String ext = FilenameUtils.getExtension(absPath);
			if (!suffixSet.contains(ext)) {
				filesSkipped++;
				return;
			}
			if (isZipFileFormatFileName(absPath)) {
				indexZipFile(path, absPath);
			} else if (isTarFileFormatFileName(absPath)) {
				indexTarFile(path, absPath, CompressionType.NONE);
			} else if (isTgzFileFormatFileName(absPath)) {
				indexTarFile(path, absPath, CompressionType.GZIP);
			} else if (isTxzFileFormatFileName(absPath)) {
				indexTarFile(path, absPath, CompressionType.XZIP);
			} else {
				indexDoc(path, -1);
			}
		}
	}

	private void indexZipFile(Path file, String zipParent) throws Exception {
		InputStream is = null;
		try {
			is = Files.newInputStream(file);
			indexZipInputStream(is, zipParent);
		} finally {
			StreamUtil.close(is);
		}
	}

	private void indexTarFile(Path file, String zipParent, CompressionType compressionType) throws Exception {
		InputStream is = null;
		try {
			is = Files.newInputStream(file);
			indexTarInputStream(is, zipParent, compressionType);
		} finally {
			StreamUtil.close(is);
		}
	}

	private void indexZipInputStream(InputStream is, String zipParent) throws Exception {
		ZipInputStream zis = new ZipInputStream(is);
		indexZipStream(zis, zipParent);
	}

	private void indexTarInputStream(InputStream is, String zipParent, CompressionType compressionType) throws Exception {
		InputStream bi = new BufferedInputStream(is);
		InputStream istream = null;
		switch (compressionType) {
		case NONE: 
			istream = bi;
			break;
		case GZIP: 
			istream = new GzipCompressorInputStream(bi);
			break;
		case XZIP: 
			istream = new XZCompressorInputStream(bi);
			break;
		default: 
			throw new Exception("Invalid compression type.");
		}
		TarArchiveInputStream tis = new TarArchiveInputStream(istream);
		indexTarStream(tis, zipParent);
	}

	private void indexZipStream(ZipInputStream zis, String zipParent) throws Exception {
		ZipEntry entry;
		while ((entry = zis.getNextEntry()) != null) {
			if (entry.isDirectory()) {
			} else 
			/*
				 * WARNING: This method is here for clarity but usually will NOT BE CALLED. The Zip file format
				 * doesn't require folders to be stored but only FILES, and actually the full path on each file is
				 * what determines the hierarchy.
				 */
			// processDirectory(entry);
			{
				String absPath = entry.getName();
				// if we encountered a zip file inside a zip file this is the recursion for
				// that.
				if (isZipFileFormatFileName(absPath)) {
					// todo-2: (or tar! handle tars)
					indexZipInputStream(zis, zipParent + "->" + absPath);
				} else {
					indexZipEntry(entry, zis, zipParent);
				}
			}
			zis.closeEntry();
		}
	}

	private void indexTarStream(TarArchiveInputStream tis, String zipParent) throws Exception {
		try {
			ArchiveEntry entry = null;
			while ((entry = tis.getNextEntry()) != null) {
				if (!tis.canReadEntryData(entry)) {
					log.warn("Can\'t read entry." + entry.getName());
					continue;
				}
				if (entry.isDirectory()) {
				} else 
				// todo-2: I know for ZIPs we can ignore directories, but I'm not sure if this
				// is the case for TAR files. Check this.
				{
					String absPath = entry.getName();
					log.debug("TAR ENTRY:" + absPath);
					// todo-2: oops ZIP is missing here. plain zip in a tar
					// todo-2: need to also support a tar in a zip
					if (isTarFileFormatFileName(absPath)) {
						indexTarInputStream(tis, zipParent + "->" + absPath, CompressionType.NONE);
					} else if (isTgzFileFormatFileName(absPath)) {
						indexTarInputStream(tis, zipParent + "->" + absPath, CompressionType.GZIP);
					} else if (isTxzFileFormatFileName(absPath)) {
						indexTarInputStream(tis, zipParent + "->" + absPath, CompressionType.XZIP);
					} else {
						indexTarEntry(entry, tis, zipParent);
					}
				}
				// NOTE: There's no entry.close() method. This is not a bug, or mistake.
			}
		} finally {
		}
		// We leave this stream open here
		// StreamUtil.close(ais);
	}

	private void indexZipEntry(ZipEntry entry, ZipInputStream zis, String zipParent) throws IOException {
		long lastModified = entry.getLastModifiedTime().toMillis();
		String absPath = entry.getName();
		// int lastSlashIdx = name.lastIndexOf("/");
		// String fileName = name.substring(lastSlashIdx + 1);
		// String path = name.substring(0, lastSlashIdx);
		String ext = FilenameUtils.getExtension(absPath);
		if (!suffixSet.contains(ext)) {
			log.debug("SKIP ZipEntry: " + absPath);
			filesSkipped++;
			return;
		}
		log.debug("INDEXING ZipEntry: " + absPath);
		// InputStream stream = Files.newInputStream(file);
		Document doc = new Document();
		/*
		 * Add the path of the file as a field named "path". Use a field that is indexed (i.e. searchable),
		 * but don't tokenize the field into separate words and don't index term frequency or positional
		 * information:
		 */
		Field pathField = new StringField("path", zipParent + "->" + absPath, Field.Store.YES);
		doc.add(pathField);
		/*
		 * Add the last modified date of the file a field named "modified". Use a LongPoint that is indexed
		 * (i.e. efficiently filterable with PointRangeQuery). This indexes to milli-second resolution,
		 * which is often too fine. You could instead create a number based on
		 * year/month/day/hour/minutes/seconds, down the resolution you require. For example the long value
		 * 2011021714 would mean February 17, 2011, 2-3 PM.
		 */
		doc.add(new LongPoint("modified", lastModified));
		/*
		 * Add the contents of the file to a field named "contents". Specify a Reader, so that the text of
		 * the file is tokenized and indexed, but not stored. Note that FileReader expects the file to be in
		 * UTF-8 encoding. If that's not the case searching for special characters will fail. doc.add(new
		 * TextField("contents", new BufferedReader(new InputStreamReader(stream,
		 * StandardCharsets.UTF_8))));
		 */
		try {
			doc.add(new TextField("contents", parseContent(zis), Store.NO));
		} catch (Exception e) {
			log.warn("Failed to tika parse: " + absPath, e);
		}
		// log.debug(" content parse complete.");
		if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
			// New index, so we just add the document (no old document can be there):
			log.debug("adding");
			writer.addDocument(doc);
			filesAdded++;
		} else {
			// Existing index (an old copy of this document may have been indexed) so
			// we use updateDocument instead to replace the old one matching the exact
			// path, if present:
			log.debug("updating");
			writer.updateDocument(new Term("path", zipParent + "->" + absPath), doc);
			filesUpdated++;
		}
		// log.debug(" content write complete.");
	}

	private void indexTarEntry(ArchiveEntry entry, TarArchiveInputStream zis, String zipParent) throws IOException {
		long lastModified = entry.getLastModifiedDate().getTime();
		String absPath = entry.getName();
		// int lastSlashIdx = name.lastIndexOf("/");
		// String fileName = name.substring(lastSlashIdx + 1);
		// String path = name.substring(0, lastSlashIdx);
		String ext = FilenameUtils.getExtension(absPath);
		if (!suffixSet.contains(ext)) {
			log.debug("SKIP TarEntry: " + absPath);
			filesSkipped++;
			return;
		}
		log.debug("INDEXING TarEntry: " + absPath);
		// InputStream stream = Files.newInputStream(file);
		Document doc = new Document();
		// Add the path of the file as a field named "path". Use a
		// field that is indexed (i.e. searchable), but don't tokenize
		// the field into separate words and don't index term frequency
		// or positional information:
		Field pathField = new StringField("path", zipParent + "->" + absPath, Field.Store.YES);
		doc.add(pathField);
		/*
		 * Add the last modified date of the file a field named "modified". Use a LongPoint that is indexed
		 * (i.e. efficiently filterable with PointRangeQuery). This indexes to milli-second resolution,
		 * which is often too fine. You could instead create a number based on
		 * year/month/day/hour/minutes/seconds, down the resolution you require. For example the long value
		 * 2011021714 would mean February 17, 2011, 2-3 PM.
		 */
		doc.add(new LongPoint("modified", lastModified));
		/*
		 * Add the contents of the file to a field named "contents". Specify a Reader, so that the text of
		 * the file is tokenized and indexed, but not stored. Note that FileReader expects the file to be in
		 * UTF-8 encoding. If that's not the case searching for special characters will fail. doc.add(new
		 * TextField("contents", new BufferedReader(new InputStreamReader(stream,
		 * StandardCharsets.UTF_8))));
		 */
		try {
			doc.add(new TextField("contents", parseContent(zis), Store.NO));
		} catch (Exception e) {
			log.warn("Failed to tika parse: " + absPath, e);
		}
		// log.debug(" content parse complete.");
		if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
			// New index, so we just add the document (no old document can be there):
			log.debug("adding");
			writer.addDocument(doc);
			filesAdded++;
		} else {
			/*
			 * Existing index (an old copy of this document may have been indexed) so we use updateDocument
			 * instead to replace the old one matching the exact path, if present:
			 */
			log.debug("updating");
			writer.updateDocument(new Term("path", zipParent + "->" + absPath), doc);
			filesUpdated++;
		}
		// log.debug(" content write complete.");
	}

	public static String parseContent(InputStream stream) throws Exception {
		Parser parser = new AutoDetectParser();
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();
		ParseContext context = new ParseContext();
		try {
			parser.parse(stream, handler, metadata, context);
			// log.debug("META: " + XString.prettyPrint(metadata));
			return handler.toString();
		} catch (ZeroByteFileException e) {
			return "";
		}
		// Another way...(that I never tried)
		// String content = TikaAnalysis.extractContentUsingParser(stream);
		// stream.close();
	}

	/**
	 * Indexes a single document
	 */
	private void indexDoc(Path file, long lastModified) throws IOException {
		if (lastModified == -1) {
			lastModified = Files.getLastModifiedTime(file).toMillis();
		}
		String absPath = file.toString();
		// String ext = FilenameUtils.getExtension(absPath);
		// if (!suffixSet.contains(ext)) {
		// log.debug("SKIP: " + absPath);
		// filesSkipped++;
		// return;
		// }
		log.debug("INDEXING: " + absPath);
		InputStream stream = Files.newInputStream(file);
		try {
			Document doc = new Document();
			// Add the path of the file as a field named "path". Use a
			// field that is indexed (i.e. searchable), but don't tokenize
			// the field into separate words and don't index term frequency
			// or positional information:
			Field pathField = new StringField("path", file.toString(), Field.Store.YES);
			doc.add(pathField);
			// Add the last modified date of the file a field named "modified".
			// Use a LongPoint that is indexed (i.e. efficiently filterable with
			// PointRangeQuery). This indexes to milli-second resolution, which
			// is often too fine. You could instead create a number based on
			// year/month/day/hour/minutes/seconds, down the resolution you require.
			// For example the long value 2011021714 would mean
			// February 17, 2011, 2-3 PM.
			doc.add(new LongPoint("modified", lastModified));
			// Add the contents of the file to a field named "contents". Specify a Reader,
			// so that the text of the file is tokenized and indexed, but not stored.
			// Note that FileReader expects the file to be in UTF-8 encoding.
			// If that's not the case searching for special characters will fail.
			// doc.add(new TextField("contents", new BufferedReader(new
			// InputStreamReader(stream, StandardCharsets.UTF_8))));
			try {
				doc.add(new TextField("contents", parseContent(stream), Store.NO));
			} catch (Exception e) {
				log.warn("Failed to tika parse: " + absPath, e);
			}
			// log.debug(" content parse complete.");
			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				// New index, so we just add the document (no old document can be there):
				log.debug("adding");
				writer.addDocument(doc);
				filesAdded++;
			} else {
				// Existing index (an old copy of this document may have been indexed) so
				// we use updateDocument instead to replace the old one matching the exact
				// path, if present:
				log.debug("updating");
				writer.updateDocument(new Term("path", file.toString()), doc);
				filesUpdated++;
			}
		} finally {
			StreamUtil.close(stream);
		}
		// log.debug(" content write complete.");
	}

	public String getSummaryReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("Lucene File Indexing...\n\n");
		sb.append("Files Added: " + String.valueOf(filesAdded) + "\n");
		sb.append("Files Updated: " + String.valueOf(filesUpdated) + "\n");
		sb.append("Files Skipped: " + String.valueOf(filesSkipped) + "\n");
		return sb.toString();
	}

	// This big block of commented code is part of the original implementation
	// before a rewrite, and I just
	// haven't convinced myself yet it's time to go ahead and delete it, but it's
	// all completely obsolete.
	// /**
	// * Index a file by creating a Document and adding fields
	// */
	// private void indexFile(final File f, final String suffix) {
	// if (f.length() > 2 * 1024 * 1024 || f.isHidden() || f.isDirectory() ||
	// !f.canRead() || !f.exists() || //
	// (ok(suffix) && !f.getName().endsWith(suffix))) {
	// return;
	// }
	// index(f);
	// }
	// private void index(final File file) {
	// try {
	// boolean deletedExisting = false;
	// final Path paths = Paths.get(file.getCanonicalPath());
	// final BasicFileAttributes attr = Files.readAttributes(paths,
	// BasicFileAttributes.class);
	// final String lastModified = getAttrVal(attr, FileProperties.MODIFIED);
	// final String path = file.getCanonicalPath();
	// final String name = file.getName();
	// /*
	// * If a searcher is provided it means we need to use it to avoid if we already
	// * have this file added with an identical timestamp.
	// */
	// if (ok(searcher )) {
	// Document docFound = searcher.findByFileName(path);
	// if (ok(docFound )) {
	// /*
	// * If our index has this document with same lastModified time, then return
	// * because the index is up to date, and there's nothing we need to do
	// */
	// if (lastModified.equals(docFound.get("lastModified"))) {
	// log.info("NO CHANGE. file: {}", file.getCanonicalPath());
	// return;
	// }
	// /*
	// * If we found this doc, and it's out of date, we delete the old doc and
	// re-add
	// * below
	// */
	// else {
	// deletedExisting = true;
	// writer.deleteDocuments(new Term("filepath", path));
	// }
	// }
	// }
	// final String created = getAttrVal(attr, FileProperties.CREATED);
	// final String size = String.valueOf(attr.size());
	// final String content = FileUtils.readFileToString(file);
	// final UserPrincipal owner = Files.getOwner(paths);
	// final String username = owner.getName();
	// Document newDoc = newLuceneDoc(content, path, name, username, lastModified,
	// size, created,
	// getDocType(file));
	// writer.addDocument(newDoc);
	// if (deletedExisting) {
	// log.info("UPDATED file: {}", file.getCanonicalPath());
	// } else {
	// log.info("ADDED file: {}", file.getCanonicalPath());
	// }
	// } catch (final Exception e) {
	// log.error("Failed indexing file", e);
	// }
	// }
	/**
	 * Get date attributes
	 */
	public static String getAttrVal(BasicFileAttributes attr, FileProperties prop) {
		SimpleDateFormat format = new SimpleDateFormat(DateUtil.DATE_FORMAT);
		switch (prop) {
		case MODIFIED: 
			return format.format((attr.lastModifiedTime().toMillis()));
		case CREATED: 
			return format.format((attr.creationTime().toMillis()));
		default: 
			throw new IllegalArgumentException(prop.toString() + "is not supported.");
		}
	}

	/**
	 * Get document type
	 */
	public static String getDocType(File f) {
		final int start = f.getName().lastIndexOf(".");
		if (start == -1) return "";
		return f.getName().substring(start + 1);
	}

	/**
	 * Create lucene document from file attributes
	 */
	public static Document newLuceneDoc(String content, String path, String name, String username, String modified, String size, String created, String docType) {
		Document doc = new Document();
		doc.add(new Field("contents", content, TextField.TYPE_NOT_STORED));
		doc.add(new StringField("filepath", path, Field.Store.YES));
		doc.add(new StringField("author", username, Field.Store.YES));
		doc.add(new StringField("lastModified", modified, Field.Store.YES));
		doc.add(new StringField("size", size, Field.Store.YES));
		doc.add(new StringField("created", created, Field.Store.YES));
		doc.add(new StringField("doctype", docType, Field.Store.YES));
		return doc;
	}

	@PreDestroy
	public void close() {
		closeIndexWriter();
		closeFSDirectory();
	}

	private void closeIndexWriter() {
		if (writer != null) {
			log.info("Shutting down index writer");
			try {
				writer.close();
				writer = null;
			} catch (Exception e) {
				log.error("Failed closing writer", e);
			}
		}
	}

	private void closeFSDirectory() {
		if (fsDir != null) {
			log.info("closing FSDirectory");
			try {
				fsDir.close();
			} catch (Exception e) {
				log.error("Failed closing writer", e);
			}
			fsDir = null;
		}
	}
}
