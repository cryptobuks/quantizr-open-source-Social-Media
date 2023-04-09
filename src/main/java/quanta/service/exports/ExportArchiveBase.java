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
	private StringBuilder markdownToc = new StringBuilder();
	private StringBuilder htmlToc = new StringBuilder();

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

			recurseNode("../", "", node, nodeStack, 0, null);

			if (req.isIncludeHTML()) {
				StringBuilder out = new StringBuilder();
				appendHtmlBegin("", out);
				if (htmlToc.length() > 0) {
					out.append("<div class='toc'>Table of Contents</div>\n");
					out.append(htmlToc);
				}
				out.append(fullHtml);
				appendHtmlEnd("", out);
				addFileEntry("content.html", out.toString().getBytes(StandardCharsets.UTF_8));
			}

			if (req.isIncludeMD()) {
				StringBuilder out = new StringBuilder();
				if (markdownToc.length() > 0) {
					out.append("Table of Contents\n\n");
					out.append(markdownToc);
					out.append("\n");
				}
				out.append(fullMd);
				addFileEntry("content.md", out.toString().getBytes(StandardCharsets.UTF_8));
			}

			if (req.isIncludeJypyter()) {
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

		/*
		 * This is the header row at the top of the page. The rest of the page is children of this node
		 */
		processNodeExport(session, parentFolder, "", node, true, fileName, level, true);
		String folder = node.getIdStr();

		Iterable<SubNode> iter = read.getChildren(session, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), null, 0);
		if (iter != null) {
			for (SubNode n : iter) {
				String noExp = n.getStr(NodeProp.NO_EXPORT);
				if (noExp != null) {
					continue;
				}
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
	 * NOTE: It's correct that there's no finally block in here enforcing the closeEntry, because we let
	 * exceptions bubble all the way up to abort and even cause the zip file itself (to be deleted)
	 * since it was unable to be written to completely successfully.
	 * 
	 * fileNameCont is an output parameter that has the complete filename minus the period and
	 * extension.
	 */
	private void processNodeExport(MongoSession ms, String parentFolder, String deeperPath, SubNode node, boolean writeFile,
			Val<String> fileNameCont, int level, boolean isTopRow) {
		try {
			// log.debug("NODE [LEV:" + level + " WRITE=" + writeFile + "]: " + node.getContent());
			String nodeId = node.getIdStr();
			String fileName = nodeId;
			String content = node.getContent() != null ? node.getContent() : "";
			content = content.trim();

			if (req.isUpdateHeadings()) {
				int slashCount = StringUtils.countMatches(node.getPath(), "/");
				int lev = slashCount - baseSlashCount;
				if (lev > 6)
					lev = 6;
				content = edit.translateHeadingsForLevel(ms, content, lev);
			}

			if (writeFile && req.isIncludeToc()) {
				addToTableOfContents(level, content, nodeId);
			}
			List<Attachment> atts = node.getOrderedAttachments();

			// we save off the 'content' into htmlContent, because we need a copy that doesn't have
			// attachments inserted into it for the special case of inserting HTML attachments
			Val<String> htmlContent = new Val<>(content);
			Val<String> mdContent = new Val<>(content);

			JupyterCell cell = null;
			if (req.isIncludeJypyter()) {
				cell = new JupyterCell();
				cell.setCellType("markdown");
				cell.setSource(makeJupyterSource(mdContent.getVal()));
				jupyterCells.add(cell);
			}

			// Process all attachments just to insert File Tags into content
			if (atts != null) {
				for (Attachment att : atts) {
					// Process File Tag type attachments here first
					if (!"ft".equals(att.getPosition())) {
						continue;
					}
					handleAttachment(true, null, mdContent, deeperPath,
							req.isAttOneFolder() ? "attachments" : ("." + parentFolder), cell, writeFile, nodeId, fileName, att);
				}
			}

			if (req.isIncludeHTML()) {
				htmlContent.setVal(formatContentToHtml(node, htmlContent.getVal()));

				// special handling for htmlContent we have to do this File Tag injection AFTER the html escaping
				// and processing that's done in the line above
				if (atts != null) {
					for (Attachment att : atts) {
						// Process File Tag type attachments here first
						if (!"ft".equals(att.getPosition())) {
							continue;
						}
						handleAttachment(true, htmlContent, null, deeperPath,
								req.isAttOneFolder() ? "attachments" : ("." + parentFolder), null, writeFile, nodeId, fileName,
								att);
					}
				}
				fullHtml.append(htmlContent.getVal());
			}

			if (req.isIncludeMD()) {
				fullMd.append("\n" + mdContent.getVal() + "\n");
			}

			if (atts != null) {
				for (Attachment att : atts) {
					// Skip File Tag type attachments because they'll already have been processed above
					if ("ft".equals(att.getPosition())) {
						continue;
					}
					handleAttachment(false, null, null, deeperPath, req.isAttOneFolder() ? "attachments" : ("." + parentFolder),
							cell, writeFile, nodeId, fileName, att);
				}
			}

			if (req.isIncludeHTML()) {
				fullHtml.append("</div>\n");
			}

			if (writeFile) {
				writeFilesForNode(ms, parentFolder, node, fileNameCont, fileName, content, atts);
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void addToTableOfContents(int level, String content, String nodeId) {
		// add to table of contents
		if (content == null)
			return;
		String headerContent = content.trim();

		if (XString.isMarkdownHeading(headerContent)) {

			// chop string at newline if there's a newline
			int newLineIdx = content.indexOf("\n");
			if (newLineIdx != -1) {
				headerContent = headerContent.substring(0, newLineIdx);
			}

			int firstSpace = headerContent.indexOf(" ");
			if (firstSpace != -1) {
				String heading = headerContent.substring(firstSpace + 1);
				String linkHeading = heading.replace(" ", "-").toLowerCase();
				level--;
				String prefix = level > 0 ? "    ".repeat(level) : "";
				markdownToc.append(prefix + "* [" + heading + "](#" + linkHeading + ")\n");
				String clazz = level == 0 ? "class='topLevelToc'" : "";
				htmlToc.append("<div " + clazz + " style='margin-left: " + (25 + level * 25) + "px'><a class='tocLink' href='#" + nodeId + "'>"
						+ StringEscapeUtils.escapeHtml4(heading) + "</a></div>");
			}
		}
	}

	/* Breaks up content into a Jupyter source array */
	private List<String> makeJupyterSource(String content) {
		StringTokenizer t = new StringTokenizer(content, "\n", true);
		List<String> list = new LinkedList<>();
		String curLine = "";

		while (t.hasMoreTokens()) {
			String tok = t.nextToken();
			if ("\n".equals(tok)) {
				if (curLine.length() > 0) {
					curLine += "\n";
				}
			} else {
				if (curLine.length() > 0) {
					list.add(curLine);
				}
				curLine = tok;
			}
		}

		if (curLine.trim().length() > 0) {
			list.add(curLine);
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

	/*
	 * If 'content' is passes as non-null then the ONLY thing we do is inject any File Tags onto that
	 * content and return the content
	 */
	private void handleAttachment(boolean injectingTag, Val<String> htmlContent, Val<String> mdContent, String deeperPath,
			String parentFolder, JupyterCell cell, boolean writeFile, String nodeId, String fileName, Attachment att) {
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

		if (mimeType.startsWith("image/")) {
			if (req.isIncludeHTML()) {
				String htmlLink = appendImgLink(nodeId, binFileNameStr, fullUrl);
				processHtmlAtt(injectingTag, htmlContent, att, htmlLink);
			}

			if (req.isIncludeMD() || cell != null) {
				String mdLink = "\n![" + binFileNameStr + "](" + fullUrl + ")\n";
				processMdAtt(injectingTag, mdContent, cell, att, mdLink);
			}
		} else {
			if (req.isIncludeHTML()) {
				String htmlLink = appendNonImgLink(binFileNameStr, fullUrl);
				processHtmlAtt(injectingTag, htmlContent, att, htmlLink);
			}

			if (req.isIncludeMD() || cell != null) {
				String mdLink = "\n[" + binFileNameStr + "](" + fullUrl + ")\n";
				processMdAtt(injectingTag, mdContent, cell, att, mdLink);
			}
		}
	}

	private void processHtmlAtt(boolean injectingTag, Val<String> htmlContent, Attachment att, String imgLink) {
		if (injectingTag) {
			if (htmlContent != null) {
				htmlContent.setVal(insertHtmlLink(htmlContent.getVal(), att, imgLink));
			}
		} else {
			fullHtml.append(imgLink);
		}
	}

	private void processMdAtt(boolean injectingTag, Val<String> mdContent, JupyterCell cell, Attachment att, String mdLink) {
		if (injectingTag) {
			if (mdContent != null) {
				mdContent.setVal(insertMdLink(mdContent.getVal(), att, mdLink));
			}
		} else {
			if (req.isIncludeMD()) {
				fullMd.append(mdLink);
			}
		}

		if (cell != null) {
			processAttachmentInCell(injectingTag, cell, att, mdLink);
		}
	}

	private void processAttachmentInCell(boolean injectingTag, JupyterCell cell, Attachment att, String mdLink) {
		if (injectingTag) {
			List<String> newSource = new LinkedList<>();
			for (String val : cell.getSource()) {
				newSource.add(insertMdLink(val, att, mdLink));
			}
			cell.setSource(newSource);
		} else {
			cell.getSource().add(mdLink);
		}
	}

	private String insertHtmlLink(String content, Attachment att, String imgLink) {
		if ("ft".equals(att.getPosition())) {
			// This replacement is kind of tricky because we have to close out the markdown div
			// then inject our HTML, and then reopen a new div so keep the markdown separate from the
			// RAW html "imgLink" we're inserting here.
			content =
					content.replace("{{" + att.getFileName() + "}}", "\n</div>" + imgLink + "<div class='markdown container'>\n");
		}
		return content;
	}

	private String insertMdLink(String content, Attachment att, String mdLink) {
		if ("ft".equals(att.getPosition())) {
			content = content.replace("{{" + att.getFileName() + "}}", mdLink);
		}

		return content;
	}

	private String appendImgLink(String nodeId, String binFileNameStr, String url) {
		return "<div class='attachment'><img title='" + binFileNameStr + "' id='img_" + nodeId
				+ "' style='width:50%' onclick='document.getElementById(\"img_" + nodeId + "\").style.width=\"\"' src='" + url
				+ "'/></div>";
	}

	private String appendNonImgLink(String binFileNameStr, String url) {
		return "<div class='attachment'><a class='link' target='_blank' href='" + url + "'>" + binFileNameStr + "</a></div>";
	}

	private String formatContentToHtml(SubNode node, String content) {
		String escapedContent = StringEscapeUtils.escapeHtml4(content);
		if (node.isType(NodeType.PLAIN_TEXT)) {
			return "\n<pre>" + escapedContent + "\n</pre>\n";
		} else {
			String prefix = "";
			if (req.isDividerLine()) {
				prefix += "<hr>";
			}
			if (req.isIncludeIDs()) {
				prefix += "\n<div class='floatContainer'><div class='floatRight'>\nID:" + node.getIdStr() + "</div></div>";
			}
			return prefix + "\n<div id='" + node.getIdStr() + "' class='markdown container'>" + escapedContent + "\n</div>\n";
		}
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
