package quanta.service.exports;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.ExportRequest;
import quanta.response.ExportResponse;
import quanta.util.ExUtil;
import quanta.util.FileUtils;
import quanta.util.ImageUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Exporter using PDFBox
 * 
 * This was experimental code, and it not currently being used. We are using the flexbox code for
 * exporting to PDF (see ExportServiceFlexmark.java)
 */

@Component
@Scope("prototype")
@Slf4j 
public class ExportPdfServicePdfBox extends ServiceBase {
	private MongoSession session;

	private String shortFileName;
	private String fullFileName;

	private PDFont font = PDType1Font.HELVETICA;
	private float baseFontSize = 12;
	private float fontSize = baseFontSize;
	private float leading;
	private int lineCount = 0;

	private PDPageContentStream stream = null;
	private PDDocument doc = null;

	private PDRectangle mediabox;
	private float margin = 35;
	private float width;
	private float startX;
	private float startY;
	private int lastSpace = -1;

	private ExportRequest req;

	/*
	 * Exports the node specified in the req. If the node specified is "/", or the repository root, then
	 * we don't expect a filename, because we will generate a timestamped one.
	 */
	public void export(MongoSession ms, ExportRequest req, ExportResponse res) {
		ms = ThreadLocals.ensure(ms);
		this.session = ms;
		this.req = req;
		String nodeId = req.getNodeId();

		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist");
		}

		if (nodeId.equals("/")) {
			throw ExUtil.wrapEx("Exporting entire repository is not supported.");
		} else {
			log.info("Exporting to File");
			exportNodeToFile(ms, nodeId);
			res.setFileName(shortFileName);
		}

		res.setSuccess(true);
	}

	private void exportNodeToFile(MongoSession ms, String nodeId) {
		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist.");
		}

		setFontSize(baseFontSize);

		SubNode exportNode = read.getNode(ms, nodeId, true, null);
		String fileName = snUtil.getExportFileName(req.getFileName(), exportNode);
		shortFileName = fileName + ".pdf";
		fullFileName = prop.getAdminDataFolder() + File.separator + shortFileName;

		try {
			// log.debug("Export Node: " + exportNode.getPath() + " to file " + fullFileName);
			doc = new PDDocument();
			newPage();
			recurseNode(exportNode, 0);
		} catch (Exception ex) {
			throw ExUtil.logAndWrapEx(log, "Failed Generating PDF", ex);
		} finally {
			try {
				if (stream != null) {
					stream.close();
				}

				doc.save(fullFileName);
				doc.close();
			} catch (Exception e) {
				throw ExUtil.logAndWrapEx(log, "Failed Unwinding PDF", e);
			}

			(new File(fullFileName)).deleteOnExit();
		}
	}

	private void newPage() {
		try {
			if (stream != null) {
				stream.close();
			}

			PDPage page = new PDPage();
			mediabox = page.getMediaBox();

			lineCount = 0;
			width = mediabox.getWidth() - 2 * margin;
			startX = mediabox.getLowerLeftX() + margin;
			startY = mediabox.getUpperRightY() - margin;

			doc.addPage(page);

			stream = new PDPageContentStream(doc, page);
			stream.setLeading(leading);
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void recurseNode(SubNode node, int level) {
		if (node == null)
			return;

		processNode(node);
		Sort sort = Sort.by(Sort.Direction.ASC, SubNode.ORDINAL);

		for (SubNode n : read.getChildren(session, node, sort, null, 0)) {
			recurseNode(n, level + 1);
		}
	}

	private void processNode(SubNode node) {
		try {
			String content = node.getContent();
			setFontSizeFromMarkdown(content);
			printContent(content);
			writeImage(node);
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void writeImage(SubNode node) {
		try {
			List<Attachment> atts = node.getOrderedAttachments();
			if (atts == null)
				return;

			// process all attachments specifically to embed the image ones
			for (Attachment att : atts) {
				if (att.getBin() == null)
					continue;

				String mime = att.getMime();
				if (!ImageUtil.isImageMime(mime)) continue;
				String imgSize = att.getCssSize();
				float sizeFactor = 1f;

				if (imgSize != null && imgSize.endsWith("%")) {
					imgSize = XString.stripIfEndsWith(imgSize, "%");
					int size = Integer.parseInt(imgSize);
					sizeFactor = Float.valueOf(size).floatValue() / 100;
				}

				InputStream is = attach.getStream(session, att.getKey(), node, false);
				if (is == null)
					continue;

				PDImageXObject pdImage = null;
				try {
					if ("image/jpeg".equals(mime) || "image/jpg".equals(mime)) {
						pdImage = JPEGFactory.createFromStream(doc, is);
					} else if ("image/gif".equals(mime) || "image/bmp".equals(mime) || "image/png".equals(mime)) {
						BufferedImage bim = ImageIO.read(is);
						pdImage = LosslessFactory.createFromImage(doc, bim);
					}
				} finally {
					IOUtils.closeQuietly(is);
				}

				if (pdImage == null)
					continue;

				float imgWidth = width * sizeFactor;
				float scale = imgWidth / pdImage.getWidth();
				advanceY(pdImage.getHeight() * scale);

				stream.drawImage(pdImage, startX, startY, imgWidth, pdImage.getHeight() * scale);
				advanceY(leading);
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void advanceY(float delta) {
		startY -= delta;

		if (startY <= margin) {
			newPage();
			startY -= delta;
		}
	}

	private void setFontSizeFromMarkdown(String text) {
		if (text.startsWith("##### ")) {
			setFontSize(baseFontSize + 2);
		} else if (text.startsWith("#### ")) {
			setFontSize(baseFontSize + 4);
		} else if (text.startsWith("### ")) {
			setFontSize(baseFontSize + 6);
		} else if (text.startsWith("## ")) {
			setFontSize(baseFontSize + 8);
		} else if (text.startsWith("# ")) {
			setFontSize(baseFontSize + 10);
		} else {
			setFontSize(baseFontSize);
		}
	}

	private void print(String val) {
		try {
			if (lineCount > 0) {
				advanceY(leading);
			}

			stream.beginText();
			stream.setFont(font, fontSize);
			stream.newLineAtOffset(startX, startY);

			// I think once you call endText you can't expect newLine to work again without
			// calling newLineAtOffset again first.
			// stream.newLine();

			stream.showText(val);
			stream.endText();
			lineCount++;
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void printContent(String wholeLetter) {
		try {
			String[] paragraphs = wholeLetter.split(System.getProperty("line.separator"));

			for (String para : paragraphs) {
				lastSpace = -1;
				printParagraph(para);
				advanceY(leading);
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private void printParagraph(String para) {
		try {
			while (para.length() > 0) {
				int spaceIdx = para.indexOf(' ', lastSpace + 1);
				if (spaceIdx < 0) {
					spaceIdx = para.length();
				}
				String subStr = para.substring(0, spaceIdx);
				float size = fontSize * font.getStringWidth(subStr) / 1000;
				if (size > width) {
					if (lastSpace < 0) {
						lastSpace = spaceIdx;
					}
					subStr = para.substring(0, lastSpace);
					print(subStr);
					para = para.substring(lastSpace).trim();
					lastSpace = -1;
				} else if (spaceIdx == para.length()) {
					print(para);
					para = "";
				} else {
					lastSpace = spaceIdx;
				}
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	public void setFontSize(float fontSize) {
		this.fontSize = fontSize;

		// this conditional is just to make sure we don't get too much space below title
		// lines.
		if (fontSize > baseFontSize + 4) {
			this.leading = 1.5f * (baseFontSize + 4);
		} else {
			this.leading = 1.5f * fontSize;
		}
	}
}
