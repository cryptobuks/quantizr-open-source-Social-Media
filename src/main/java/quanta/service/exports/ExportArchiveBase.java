package quanta.service.exports;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.jupyter.JupyterCell;
import quanta.model.jupyter.JupyterCodeMirrorMode;
import quanta.model.jupyter.JupyterLangInfo;
import quanta.model.jupyter.JupyterMetadata;
import quanta.model.jupyter.JupyterKernelSpec;
import quanta.model.jupyter.JupyterNB;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.ExportRequest;
import quanta.response.ExportResponse;
import quanta.util.ExUtil;
import quanta.util.FileUtils;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Base class for exporting to archives (ZIP and TAR).
 * 
 * NOTE: Derived classes are expected to be 'prototype' scope so we can keep state in this object on
 * a per-export basis. That is, each time a user does an export, a new instance of this class is
 * created that is dedicated just do doing that one export and so any member varibles in this class
 * have just that one export as their 'scope'
 */
@Slf4j
public abstract class ExportArchiveBase extends ServiceBase {
	private String shortFileName;
	private String fullFileName;
	private String rootPathParent;
	ExportRequest req;
	int baseSlashCount = 0;

	/*
	 * It's possible that nodes recursively contained under a given node can have same name, so we have
	 * to detect that and number them, so we use this hashset to detect existing filenames.
	 */
	private final HashSet<String> fileNameSet = new HashSet<>();

	private MongoSession session;
	private StringBuilder fullHtml = new StringBuilder();
	private StringBuilder fullMd = new StringBuilder();
	private StringBuilder toc = new StringBuilder();

	private List<JupyterCell> jupyterCells = new LinkedList<>();

	public void export(MongoSession ms, ExportRequest req, ExportResponse res) {
		ms = ThreadLocals.ensure(ms);
		this.req = req;
		this.session = ms;

		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist: " + prop.getAdminDataFolder());
		}

		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		String fileName = snUtil.getExportFileName(req.getFileName(), node);
		shortFileName = fileName + "." + getFileExtension();
		fullFileName = prop.getAdminDataFolder() + File.separator + shortFileName;

		if (req.isUpdateHeadings()) {
			baseSlashCount = StringUtils.countMatches(node.getPath(), "/");
		}

		boolean success = false;
		try {
			openOutputStream(fullFileName);

			if (req.isIncludeHTML()) {
				writeRootFiles();
			}

			rootPathParent = node.getParentPath();
			auth.ownerAuth(ms, node);
			ArrayList<SubNode> nodeStack = new ArrayList<>();
			nodeStack.add(node);

			if (req.isIncludeHTML()) {
				appendHtmlBegin("", fullHtml);
			}

			recurseNode("../", "", node, nodeStack, 0, null);

			if (req.isIncludeHTML()) {
				appendHtmlEnd("", fullHtml);
				addFileEntry("content.html", fullHtml.toString().getBytes(StandardCharsets.UTF_8));
			}

			if (req.isIncludeMD()) {
				StringBuilder out = new StringBuilder();
				if (toc.length() > 0) {
					out.append("Table of Contents\n\n");
					out.append(toc);
					out.append("\n");
				}
				out.append(fullMd);
				addFileEntry("content.md", out.toString().getBytes(StandardCharsets.UTF_8));
			}

			if (req.isJupyterFile()) {
				addFileEntry("content.ipynb", XString.prettyPrint(makeJupyterNotebook()).getBytes(StandardCharsets.UTF_8));
			}

			res.setFileName(shortFileName);
			success = true;
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			closeOutputStream();

			if (!success) {
				FileUtils.deleteFile(fullFileName);
			}
		}

		res.setSuccess(true);
	}

	private JupyterNB makeJupyterNotebook() {
		return new JupyterNB(jupyterCells, //
				new JupyterMetadata(//
						new JupyterKernelSpec("Python 3", "python", "python3"), //
						new JupyterLangInfo(//
								new JupyterCodeMirrorMode("ipython", 3), ".py", //
								"text/x-python", "python", "python", //
								"ipython3", "3.10.6"),
						4), //
				4, 2);
	}

	private void writeRootFiles() {
		// These files are how our exported HTML content get the ability to render markdown content.
		writeRootFile("exported.js");
		writeRootFile("marked.min.js");
		writeRootFile("exported.css");
	}

	private void writeRootFile(String fileName) {
		InputStream is = null;
		String resourceName = "classpath:/public/export-includes/" + fileName;
		try {
			Resource resource = context.getResource(resourceName);
			is = resource.getInputStream();
			byte[] targetArray = IOUtils.toByteArray(is);
			addFileEntry(fileName, targetArray);
		} catch (Exception e) {
			throw new RuntimeEx("Unable to write resource: " + resourceName, e);
		} finally {
			StreamUtil.close(is);
		}
	}

	private void recurseNode(String rootPath, String parentFolder, SubNode node, ArrayList<SubNode> nodeStack, int level,
			String parentId) {
		if (node == null)
			return;

		// If a node has a property "noexport" (added by power users) then this node will not be exported.
		String noExport = node.getStr(NodeProp.NO_EXPORT);
		if (noExport != null) {
			return;
		}

		/* process the current node */
		Val<String> fileName = new Val<>();
		Iterable<SubNode> iter = read.getChildren(session, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), null, 0);

		/*
		 * This is the header row at the top of the page. The rest of the page is children of this node
		 */
		processNodeExport(session, true, req.isJupyterFile(), parentFolder, "", node, true, fileName, level, true);
		String folder = node.getIdStr();

		if (iter != null) {
			/*
			 * First pass over children is to embed their content onto the child display on the current page
			 */
			for (SubNode n : iter) {
				String noExp = n.getStr(NodeProp.NO_EXPORT);
				if (noExp != null) {
					continue;
				}

				processNodeExport(session, false, false, parentFolder, //
						"", n, false, null, level, false);
			}
		}

		if (iter != null) {
			/* Second pass over children is the actual recursion down into the tree */
			for (SubNode n : iter) {
				nodeStack.add(n);
				recurseNode(rootPath + "../", parentFolder + "/" + folder, n, nodeStack, level + 1, n.getIdStr());
				nodeStack.remove(n);
			}
		}
	}

	private void appendHtmlBegin(String rootPath, StringBuilder html) {
		html.append("<html>");
		html.append("<head>\n");
		html.append("<link rel='stylesheet' href='" + rootPath + "exported.css' />");
		html.append("</head>\n");
		html.append("<body>\n");
	}

	private void appendHtmlEnd(String rootPath, StringBuilder html) {
		html.append("<script src='" + rootPath + "marked.min.js'></script>");
		html.append("<script src='" + rootPath + "exported.js'></script>");
		html.append("</body></html>");
	}

	/*
	 * NOTE: It's correct that there's no finally block in here enforcing the closeEntry, becasue we let
	 * exceptions bubble all the way up to abort and even cause the zip file itself (to be deleted)
	 * since it was unable to be written to completely successfully.
	 * 
	 * fileNameCont is an output parameter that has the complete filename minus the period and
	 * extension.
	 */

	private void processNodeExport(MongoSession ms, boolean allowAppend, boolean appendJupyterJson, String parentFolder,
			String deeperPath, SubNode node, boolean writeFile, Val<String> fileNameCont, int level, boolean isTopRow) {
		try {
			// log.debug("NODE [LEV:" + level + " WRITE=" + writeFile + "]: " + node.getContent());
			String nodeId = node.getIdStr();
			String fileName = nodeId;

			JupyterCell cell = null;
			if (appendJupyterJson) {
				cell = new JupyterCell();
				cell.setCellType("markdown");
			}

			String content = node.getContent() != null ? node.getContent() : "";
			content = content.trim();

			if (req.isUpdateHeadings()) {
				int slashCount = StringUtils.countMatches(node.getPath(), "/");
				int lev = slashCount - baseSlashCount;
				if (lev > 6)
					lev = 6;
				content = edit.translateHeadingForLevel(ms, content, lev);
			}

			// add to table of contents
			if (req.isIncludeToc() && writeFile && content != null) {
				String headerContent = content;

				int newLineIdx = content.indexOf("\n");
				if (newLineIdx != -1) {
					headerContent = headerContent.substring(0, newLineIdx);
				}
				if (XString.isMarkdownHeading(headerContent)) {
					int firstSpace = headerContent.indexOf(" ");
					if (firstSpace != -1) {
						String heading = headerContent.substring(firstSpace + 1);
						String linkHeading = heading.replaceAll(" ", "-").toLowerCase();
						level--;
						String prefix = level > 0 ? "    ".repeat(level) : "";
						toc.append(prefix + "* [" + heading + "](#" + linkHeading + ")\n");
					}
				}
			}

			if (appendJupyterJson) {
				cell.setSource(makeJupyterSource(content));
				jupyterCells.add(cell);
			}

			if (allowAppend) {
				if (req.isIncludeHTML()) {
					appendNodeHtmlContent(node, fullHtml, content);
				}

				if (req.isIncludeMD()) {
					fullMd.append("\n" + content + "\n");
				}
			}

			List<Attachment> atts = node.getOrderedAttachments();
			if (atts != null) {
				for (Attachment att : atts) {
					appendAttachment(deeperPath, req.isAttOneFolder() ? "attachments" : ("." + parentFolder), allowAppend, cell,
							writeFile, nodeId, fileName, att);
				}
			}

			if (allowAppend && req.isIncludeHTML()) {
				fullHtml.append("</div>\n");

				if (req.isDividerLine()) {
					fullHtml.append("<hr>\n");
				}
			}

			if (writeFile) {
				writeFilesForNode(ms, parentFolder, node, fileNameCont, fileName, content, atts);
			}
		} catch (

		Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private List<String> makeJupyterSource(String content) {
		StringTokenizer t = new StringTokenizer(content, "\n\r", false);
		List<String> list = new LinkedList<>();
		while (t.hasMoreTokens()) {
			String tok = t.nextToken();
			list.add(tok + "\n");
		}
		return list;
	}

	private void writeFilesForNode(MongoSession ms, String parentFolder, SubNode node, Val<String> fileNameCont, String fileName,
			String content, List<Attachment> atts) {
		String fileNameBase = parentFolder + "/" + fileName + "/" + fileName;
		fileNameCont.setVal(fileNameBase);
		String json = getNodeJson(node);

		if (req.isIncludeJSON()) {
			addFileEntry(fileNameBase + ".json", json.getBytes(StandardCharsets.UTF_8));
		}

		if (atts != null) {
			for (Attachment att : atts) {
				writeAttachmentFileForNode(ms, parentFolder, node, fileName, att);
			}
		}
	}

	private String getNodeJson(SubNode node) {
		String json;
		/*
		 * Pretty print the node having the relative path, and then restore the node to the full path
		 */
		String fullPath = node.getPath();
		String relPath = fullPath.substring(rootPathParent.length());
		try {
			node.directSetPath(relPath);
			json = XString.prettyPrint(node);
		} finally {
			node.directSetPath(fullPath);
		}
		return json;
	}

	private void writeAttachmentFileForNode(MongoSession ms, String parentFolder, SubNode node, String fileName, Attachment att) {
		String ext = null;
		String binFileNameProp = att.getFileName();
		if (binFileNameProp != null) {
			ext = FilenameUtils.getExtension(binFileNameProp);
			if (!StringUtils.isEmpty(ext)) {
				ext = "." + ext;
			}
		}
		/*
		 * If we had a binary property on this node we write the binary file into a separate file, but for
		 * ipfs links we do NOT do this
		 */
		if (att.getMime() != null) {
			InputStream is = null;
			try {
				is = attach.getStream(ms, att.getKey(), node, false);
				if (is == null)
					return;
				BufferedInputStream bis = new BufferedInputStream(is);
				long length = att != null ? att.getSize() : null;
				String binFileName = req.isAttOneFolder() ? ("/attachments/" + fileName + "-" + att.getKey() + ext) : //
						(parentFolder + "/" + fileName + "/" + att.getKey() + ext);

				if (length > 0) {
					/* NOTE: the archive WILL fail if no length exists in this codepath */
					addFileEntry(binFileName, bis, length);
				} else {
					/*
					 * This *should* never happen that we fall back to writing as an array from the input stream because
					 * normally we will always have the length saved on the node. But re are trying to be as resilient
					 * as possible here falling back to this rather than failing the entire export
					 */
					addFileEntry(binFileName, IOUtils.toByteArray(bis));
				}

			} catch (Exception e) {
				throw ExUtil.wrapEx(e);
			} finally {
				StreamUtil.close(is);
			}
		}
	}

	private void appendAttachment(String deeperPath, String parentFolder, boolean allowAppend, JupyterCell cell,
			boolean writeFile, String nodeId, String fileName, Attachment att) {
		String ext = null;
		String binFileNameProp = att.getFileName();
		if (binFileNameProp != null) {
			ext = FilenameUtils.getExtension(binFileNameProp);
			if (!StringUtils.isEmpty(ext)) {
				ext = "." + ext;
			}
		}
		String binFileNameStr = binFileNameProp != null ? binFileNameProp : "binary";
		String mimeType = att.getMime();
		String fullUrl = parentFolder + "/" + fileName + (req.isAttOneFolder() ? "-" : "/") + att.getKey() + ext;
		String relPath = writeFile ? "" : (fileName + "/");
		String url = att.getUrl();

		// if no exernal link, this is a local file so build path to it.
		if (url == null) {
			url = "./" + deeperPath + relPath + att.getKey() + ext;
		} else {
			binFileNameStr = "External image";
		}

		if (mimeType == null)
			return;

		if (allowAppend && req.isIncludeHTML()) {
			fullHtml.append("<div class='attachment'>");
		}

		if (mimeType.startsWith("image/")) {
			String md = "\n![" + binFileNameStr + "](" + fullUrl + ")\n";
			if (allowAppend) {
				if (req.isIncludeHTML()) {
					appendImgLink(fullHtml, nodeId, binFileNameStr, fullUrl);
				}
				if (req.isIncludeMD()) {
					fullMd.append(md);
				}
			}
			if (cell != null) {
				cell.getSource().add(md);
			}
		} else {
			String md = "\n[" + binFileNameStr + "](" + fullUrl + ")\n";
			if (allowAppend) {
				if (req.isIncludeHTML()) {
					appendNonImgLink(fullHtml, binFileNameStr, fullUrl);
				}

				if (req.isIncludeMD()) {
					fullMd.append(md);
				}
			}
			if (cell != null) {
				cell.getSource().add(md);
			}
		}

		if (allowAppend && req.isIncludeHTML()) {
			fullHtml.append("</div>");
		}
	}

	private void appendImgLink(StringBuilder html, String nodeId, String binFileNameStr, String url) {
		html.append("<img title='" + binFileNameStr + "' id='img_" + nodeId
				+ "' style='width:200px' onclick='document.getElementById(\"img_" + nodeId + "\").style.width=\"\"' src='" + url
				+ "'/>");
	}

	private void appendNonImgLink(StringBuilder html, String binFileNameStr, String url) {
		html.append("<a class='link' target='_blank' href='" + url + "'>" + binFileNameStr + "</a>");
	}

	private void appendNodeHtmlContent(SubNode node, StringBuilder html, String content) {
		String escapedContent = StringEscapeUtils.escapeHtml4(content);
		if (node.isType(NodeType.PLAIN_TEXT)) {
			html.append("\n<pre>" + escapedContent + "\n</pre>\n");
		} else {
			if (req.isIncludeIDs()) {
				html.append("\n<div class='floatContainer'><div class='floatRight'>\nID:" + node.getIdStr() + "</div></div>");
			}
			html.append("\n<div class='markdown container'>" + escapedContent + "\n</div>\n");
		}
	}

	private String cleanupFileName(String fileName) {
		fileName = fileName.trim();
		fileName = FileUtils.ensureValidFileNameChars(fileName);
		fileName = XString.stripIfStartsWith(fileName, "-");
		fileName = XString.stripIfEndsWith(fileName, "-");
		return fileName;
	}

	private void addFileEntry(String fileName, byte[] bytes) {
		// log.debug("addFileEntry: " + fileName);
		/*
		 * If we have duplicated a filename, number it sequentially to create a unique file
		 */
		if (fileNameSet.contains(fileName)) {
			int idx = 1;
			String numberedFileName = fileName + String.valueOf(idx);
			while (fileNameSet.contains(numberedFileName)) {
				numberedFileName = fileName + String.valueOf(++idx);
			}
			fileName = numberedFileName;
		}

		fileNameSet.add(fileName);
		addEntry(fileName, bytes);
	}

	private void addFileEntry(String fileName, InputStream is, long length) {
		if (length <= 0) {
			throw new RuntimeEx("length is required");
		}
		/*
		 * If we have duplicated a filename, number it sequentially to create a unique file
		 */
		if (fileNameSet.contains(fileName)) {
			int idx = 1;
			String numberedFileName = fileName + String.valueOf(idx);
			while (fileNameSet.contains(numberedFileName)) {
				numberedFileName = fileName + String.valueOf(++idx);
			}
			fileName = numberedFileName;
		}

		fileNameSet.add(fileName);
		addEntry(fileName, is, length);
	}


	public abstract String getFileExtension();

	public abstract void openOutputStream(String fileName);

	public abstract void closeOutputStream();

	public abstract void addEntry(String fileName, byte[] bytes);

	public abstract void addEntry(String fileName, InputStream stream, long length);
}
