package quanta.service.exports;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.ext.toc.internal.TocOptions;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.pdf.converter.PdfConverterExtension;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import quanta.AppController;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.model.ipfs.dag.MerkleNode;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.ExportRequest;
import quanta.response.ExportResponse;
import quanta.util.ExUtil;
import quanta.util.FileUtils;
import quanta.util.ImageUtil;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * https://github.com/vsch/flexmark-java
 */
@Component
@Scope("prototype")
@Slf4j
public class ExportServiceFlexmark extends ServiceBase {
	private MongoSession session;

	private String shortFileName;
	private String fullFileName;

	private StringBuilder markdown = new StringBuilder();
	private String format;

	private ExportRequest req;
	private ExportResponse res;
	int baseSlashCount = 0;

	private List<ExportIpfsFile> files = new LinkedList<>();

	/*
	 * Exports the node specified in the req. If the node specified is "/", or the repository root, then
	 * we don't expect a filename, because we will generate a timestamped one.
	 * 
	 * Format can be 'html' or 'pdf'
	 */
	public void export(MongoSession ms, String format, ExportRequest req, ExportResponse res) {
		ms = ThreadLocals.ensure(ms);
		this.session = ms;
		this.format = format;
		this.req = req;
		this.res = res;

		String nodeId = req.getNodeId();

		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist");
		}

		if (nodeId.equals("/")) {
			throw ExUtil.wrapEx("Exporting entire repository is not supported.");
		} else {
			log.info("Exporting to Text File");
			exportNodeToFile(ms, nodeId);
			res.setFileName(shortFileName);
		}

		res.setSuccess(true);
	}

	private void exportNodeToFile(MongoSession ms, String nodeId) {
		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist.");
		}

		SubNode exportNode = read.getNode(ms, nodeId, true, null);
		String fileName = snUtil.getExportFileName(req.getFileName(), exportNode);
		shortFileName = fileName + "." + format;
		fullFileName = prop.getAdminDataFolder() + File.separator + shortFileName;
		boolean wroteFile = false;

		if (req.isUpdateHeadings()) {
			baseSlashCount = StringUtils.countMatches(exportNode.getPath(), "/");
		}

		FileOutputStream out = null;
		try {
			// Let's keep these examples commented until I have time to understand them...
			//
			// MutableDataSet options = PegdownOptionsAdapter.flexmarkOptions(
			// Extensions.ALL & ~(Extensions.ANCHORLINKS | Extensions.EXTANCHORLINKS_WRAP)
			// , TocExtension.create()).toMutable()
			// .set(TocExtension.LIST_CLASS, PdfConverterExtension.DEFAULT_TOC_LIST_CLASS)
			// .toImmutable();
			/////////////////
			// options.set(Parser.EXTENSIONS, Arrays.asList(
			// TocExtension.create(),
			// AnchorLinkExtension.create()
			// ));
			// options.set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false);

			// uncomment to convert soft-breaks to hard breaks
			// options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

			MutableDataSet options = new MutableDataSet();
			options.set(Parser.EXTENSIONS, Arrays.asList(//
					TablesExtension.create(), //
					TocExtension.create(), //
					AnchorLinkExtension.create(), //
					AutolinkExtension.create()));
			options.set(TocExtension.LEVELS, TocOptions.getLevels(1, 2, 3, 4, 5, 6));

			// This numbering works in the TOC but I haven't figured out how to number the
			// actual headings in the body of the document itself.
			// options.set(TocExtension.IS_NUMBERED, true);

			Parser parser = Parser.builder(options).build();
			HtmlRenderer renderer = HtmlRenderer.builder(options).build();

			/*
			 * if this is the node being exported. PDF generator uses this special '[TOC]' (via TocExtension) as
			 * the place where we want the table of contents injected so we can click the "Table of Contents"
			 * checkbox in the export, or theoretically we would also insert this [TOC] somewhere else in the
			 * text.
			 */
			if ("pdf".equalsIgnoreCase(format) && req.isIncludeToc()) {
				markdown.append("[TOC]");
			}

			recurseNode(exportNode, 0);

			Node document = parser.parse(markdown.toString());
			String body = renderer.render(document);
			String html = generateHtml(body);

			if ("html".equals(format)) {
				if (req.isToIpfs()) {
					writeIpfsFiles(html);
				} else {
					FileUtils.writeEntireFile(fullFileName, html);
					wroteFile = true;
				}
			} else if ("pdf".equals(format)) {
				// todo-2: We should have an OPTION to export ONLY and DIRECTLY to IPFS here, and
				// not even write to a file.
				out = new FileOutputStream(new File(fullFileName));

				/*
				 * todo-2: we're writing to a physical file here EVEN when all we need it for is to put out on IPFS.
				 * This can be improved to not need the physica file but do it either all as streams or in byte
				 * array.
				 */
				PdfConverterExtension.exportToPdf(out, html, "", options);
				wroteFile = true;
				StreamUtil.close(out);

				if (req.isToIpfs()) {
					// now write the file we just generated out to IPFS.
					FileInputStream is = null;
					try {
						is = new FileInputStream(fullFileName);

						String mime = "application/pdf";

						// -----------------------------------------------------
						// DO NOT DELETE
						// ---------------
						// this does the regular IPFS file add
						// MerkleLink ret = ipfs.addFromStream(ms, is, shortFileName, mime, null, false);
						// ipfs.writeIpfsExportNode(ms, ret.getHash(), mime, shortFileName, null);
						// res.setIpfsCid(ret.getHash());
						// ---------------
						// But let's write to MFS now instead!
						String mfsPath = "/" + ThreadLocals.getSC().getRootId() + "/exports/" + shortFileName;
						ipfsFiles.addFileFromStream(ms, mfsPath, is, mime, null);
						res.setIpfsCid("/exports/" + shortFileName);
						// ----------------------------------------------------

						res.setIpfsMime(mime);
					} finally {
						StreamUtil.close(is);
					}
				}
			} else {
				throw new RuntimeException("invalid format.");
			}

		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			StreamUtil.close(out);
			if (wroteFile) {
				(new File(fullFileName)).deleteOnExit();
			}
		}
	}

	private void writeIpfsFiles(String html) {
		String mime = "text/html";

		// generate root folder to hold all the files
		MerkleNode rootDir = ipfsObj.newObject();
		// log.debug("new rootDir: " + XString.prettyPrint(rootDir));

		// add the main html file as index.html
		MerkleLink index = ipfs.addFileFromString(session, html, "index.html", mime, false);
		rootDir = ipfsObj.addFileToDagRoot(rootDir.getHash(), "index.html", index.getHash());

		/*
		 * Next we add all the 'image' attachments that the HTML can point to (currently only supports other
		 * IPFS-type uploads (images stored on ipfs already))
		 * 
		 * This will make images work inside this DAG file using no path so an image file named
		 * 'my-image.jpg' will work in an html IMG tag with just src='my-image.jpg'.
		 * 
		 * However the tricky part is that since Quanta doesn't yet have a reverse proxy and a way for 'end
		 * users' to directly access it's IPFS gateway we embed the actual CID onto the end of the 'src' as
		 * a param like this: src='my-image.jpg?cid=Qm123456...', so the Quanta server is able to use
		 * queries like that and grab the correct data to return based on the 'cid=' arg, where as the rest
		 * of the IPFS internet gateways will hopefully ignore that unknown parameter.
		 */
		for (ExportIpfsFile file : files) {
			// todo-2: is there a way to add multiple files to a DAG all at once? Post this
			// question on discuss.ipfs.io?
			// log.debug("Add file: " + file.getFileName() + " cid=" + file.getCid());
			rootDir = ipfsObj.addFileToDagRoot(rootDir.getHash(), file.getFileName(), file.getCid());
		}

		String fullCid = rootDir.getHash() + "/index.html";
		ipfs.writeIpfsExportNode(session, fullCid, mime, "index.html", files);

		if (rootDir != null) {
			res.setIpfsCid(fullCid);
			res.setIpfsMime(mime);
		}
	}

	private void recurseNode(SubNode node, int level) {
		if (node == null)
			return;

		processNode(node);
		Sort sort = Sort.by(Sort.Direction.ASC, SubNode.ORDINAL);

		for (SubNode n : read.getChildren(session, node, sort, null, 0)) {

			// If a node has a property "sn:noexport" (added by power users) then this node will not be
			// exported.
			String noExport = n.getStr(NodeProp.NO_EXPORT);
			if (noExport != null) {
				continue;
			}
			recurseNode(n, level + 1);
		}
	}

	private void processNode(SubNode node) {
		markdown.append("\n");

		String content = node.getContent();
		if (content != null && req.isUpdateHeadings()) {
			content = content.trim();
			int slashCount = StringUtils.countMatches(node.getPath(), "/");
			int lev = slashCount - baseSlashCount;
			if (lev > 6)
				lev = 6;
			content = edit.translateHeadingsForLevel(session, content, lev);
		}

		markdown.append(content);
		markdown.append("\n");
		writeImages(node);
	}

	private void writeImages(SubNode node) {
		List<Attachment> atts = node.getOrderedAttachments();
		if (atts == null)
			return;

		// process all attachments specifically to embed the image ones
		for (Attachment att : atts) {
			String mime = att.getMime();
			if (!ImageUtil.isImageMime(mime))
				continue;

			String bin = att.getBin();
			String url = att.getUrl();
			String ipfsLink = att.getIpfsLink();

			if (bin == null && ipfsLink == null && url == null) {
				continue;
			}

			String style = "";
			String imgSize = att.getCssSize();
			if (imgSize != null && (imgSize.endsWith("%") || imgSize.endsWith("px"))) {
				style = " style='width:" + imgSize + "'";
			} else {
				// For large enough images if they're left to actual size that can clip in the final PDF output
				// so we set any images big enough that we know they're not a thubnail or icon depiction to 100%
				// always
				if (att.getWidth() > 500) {
					style = " style='width:100%'";
				}
			}

			String src = null;

			if (req.isToIpfs() && "html".equals(format)) {
				String fileName = att.getFileName();

				if (bin != null) {
					String cid = ipfs.saveNodeAttachmentToIpfs(session, node);
					// log.debug("Saved NodeID bin to IPFS: got CID=" + cid);
					files.add(new ExportIpfsFile(cid, fileName, mime));
					src = fileName + "?cid=" + cid;
				}
				/*
				 * if this is already an IPFS linked thing, assume we're gonna have it's name added in the DAG and
				 * so reference it in src
				 */
				else if (ipfsLink != null && fileName != null) {
					// log.debug("Found IPFS file: " + fileName);
					files.add(new ExportIpfsFile(ipfsLink, fileName, mime));

					/*
					 * NOTE: Since Quanta doesn't run a reverse proxy currently and doesn't have it's IPFS gateway open
					 * to the internet we have to use this trick if sticking on the cid parameter so that our
					 * AppController.getBinary function (which will be called when the user references the resuorce) can
					 * use that instead of the relative path to locate the file.
					 * 
					 * When normal other IPFS gateways are opening this content they'll reference the actual 'fileName'
					 * and it will work because we do DAG-link that file into the root CID DAG entry for this export!
					 */
					src = fileName + "?cid=" + ipfsLink;
				}
			}
			/*
			 * NOTE: When exporting to PDF (wither with or without IPFS export option) we have to generate this
			 * kind of reference to the image resource, because ultimately the Flexmark code that converts the
			 * HTML to the PDF will be calling this image url to extract out the actual image data to embed
			 * directly into the PDF file so also in this case it doesn't matter if the PDF is going to be
			 * eventually put out on IPFS or simply provided to the user as a downloadable link.
			 */
			else if (bin != null) {
				String path = AppController.API_PATH + "/bin/" + bin + "?nodeId=" + node.getIdStr() + "&token="
						+ URLEncoder.encode(ThreadLocals.getSC().getUserToken(), StandardCharsets.UTF_8);
				src = prop.getHostAndPort() + path;
			} else if (url != null) {
				src = url;
			}

			if (src == null)
				continue;

			/*
			 * I'm not wrapping this img in a div, so they don't get forced into a vertical display of images,
			 * but the PDF engine seems to be able to smartly insert images in an attractive way arranging small
			 * images side-by-side when they'll fit on the page so I'm just letting the PDF determine how to
			 * position images, since it seems ok
			 */
			markdown.append("\n<img src='" + src + "' " + style + "/>\n");
		}
	}

	/**
	 * Wraps the generated content (html body part) into a larger complete HTML file
	 */
	private String generateHtml(String body) {
		String ret = XString.getResourceAsString(context, "/public/export-includes/flexmark/html-template.html");
		ret = ret.replace("{{hostAndPort}}", prop.getHostAndPort());
		ret = ret.replace("{{body}}", body);
		return ret;
	}
}

////////////////////////////////////////////////////////////////
// DO NOT DELETE
//
// This is the code to do export to DOCX (MS Word) files but
// according to LibreOffice the file it generates is invalid, so
// I'm mothballing the code in this comment block for future reference
//
// I never looked deep into this however:
// https://github.com/vsch/flexmark-java/blob/c0313d67e0146292a10d04eb8944faff991579e9/flexmark-docx-converter/src/test/java/com/vladsch/flexmark/docx/converter/ComboDocxConverterSpecTestBase.java#L58
////////////////////////////////////////////////////////////////
//
// <dependency>
// <groupId>com.vladsch.flexmark</groupId>
// <artifactId>flexmark-docx-converter</artifactId>
// <version>0.62.2</version>
// </dependency>

// <dependency>
// <groupId>org.docx4j</groupId>
// <artifactId>docx4j-JAXB-ReferenceImpl</artifactId>
// <version>8.1.0</version>
// </dependency>

// MutableDataSet options =
// new MutableDataSet()
// .set(Parser.EXTENSIONS, Arrays.asList(
// // DefinitionExtension.create(),
// // EmojiExtension.create(),
// // FootnoteExtension.create(),
// // StrikethroughSubscriptExtension.create(),
// // InsExtension.create(),
// // SuperscriptExtension.create(),
// TablesExtension.create(),
// TocExtension.create()
// // SimTocExtension.create(),
// // WikiLinkExtension.create()
// ))
// .set(DocxRenderer.SUPPRESS_HTML, true)
// // the following two are needed to allow doc relative and site relative
// address resolution
// //.set(DocxRenderer.DOC_RELATIVE_URL, "") // this will be used for URLs like
// 'images/...' or './' or '../'
// //.set(DocxRenderer.DOC_ROOT_URL, "") // this will be used for URLs like:
// '/...'
// ;

// Parser PARSER = Parser.builder(options).build();
// DocxRenderer RENDERER = DocxRenderer.builder(options).build();

// recurseNode(exportNode, 0);
// Node document = PARSER.parse(markdown.toString());

// // to get XML
// String xml = RENDERER.render(document);

// // or to control the package
// WordprocessingMLPackage template = DocxRenderer.getDefaultTemplate();
// RENDERER.render(document, template);

// File file = new File(fullFileName);
// try {
// template.save(file);
// } catch (Docx4JException e) {
// e.printStackTrace();
// }
