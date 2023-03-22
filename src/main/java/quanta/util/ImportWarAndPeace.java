package quanta.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.NodeType;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;

// todo-2: need to look into bulk-ops for doing this
// tips:
// https://stackoverflow.com/questions/26657055/spring-data-mongodb-and-bulk-update
// BulkOperations ops = template.bulkOps(BulkMode.UNORDERED, Match.class);
// for (User user : users) {
// Update update = new Update();
// ...
// ops.updateOne(query(where("id").is(user.getId())), update);
// }
// ops.execute();

/**
 * Reads the special proprietary-formatted file (not invented by the Quantizr developers) of the
 * book 'War and Peace' to load into the repository, because we use this book as sample/example
 * content.
 */

@Component
@Scope("prototype")
@Slf4j 
public class ImportWarAndPeace extends ServiceBase {
	private int maxLines = Integer.MAX_VALUE;
	private int maxBooks = Integer.MAX_VALUE;

	private boolean debug = false;
	private SubNode root;
	private SubNode curBook;
	private SubNode curChapter;

	StringBuilder paragraph = new StringBuilder();

	private int globalBook = 0;
	private boolean halt;
	private MongoSession session;

	public void importBook(MongoSession ms, String resourceName, SubNode root, int maxBooks) {
		try {
			this.root = root;
			this.session = ms;
			this.maxBooks = maxBooks;
			Resource resource = context.getResource(resourceName);
			InputStream is = resource.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(is));

			try {
				String line;
				int lineCount = 0;

				while (!halt && (line = in.readLine()) != null) {
					line = line.trim();

					/*
					 * if we see a blank line we add the current paragraph text as a node and continue
					 */
					if (line.length() == 0) {
						if (paragraph.length() > 0) {
							addParagraph();
						}
						continue;
					}

					if (debug) {
						log.debug("INPUT: " + line);
					}

					/*
					 * if we processed the chapter, the last paragraph is also added before starting the new chapter
					 */
					if (processChapter(line)) {
						continue;
					}

					/*
					 * if we processed the book, the last paragraph is also added before starting the new book
					 */
					if (processBook(line)) {
						continue;
					}
					if (globalBook > maxBooks)
						break;

					/* keep appending each line to the current paragraph */
					if (paragraph.length() > 0) {
						paragraph.append(" ");
					}
					paragraph.append(line);

					if (++lineCount > maxLines)
						break;
				}
			} finally {
				StreamUtil.close(in);
			}
			log.debug("book import successful.");
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	private boolean processChapter(String line) {
		if (line.startsWith("CHAPTER ")) {
			log.debug("Processing Chapter: " + line);
			if (curBook == null)
				throw ExUtil.wrapEx("book is null.");

			addParagraph();

			curChapter = create.createNode(session, curBook, NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, true);
			curChapter.setContent(/* "C" + String.valueOf(globalChapter) + ". " + */ line);
			curChapter.touch();
			update.save(session, curChapter);
			return true;
		}

		return false;
	}

	private boolean addParagraph() {
		String line = paragraph.toString();

		/*
		 * remove any places where my algorithm stuffed an extra space that just happened to be at a
		 * sentence end
		 */
		line = line.replace(".   ", ".  ");

		if (line.length() == 0)
			return false;
		if (curChapter == null || curBook == null)
			return false;

		// line = XString.injectForQuotations(line);

		SubNode paraNode = create.createNode(session, curChapter, NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, true);
		paraNode.setContent(/* "VS" + globalVerse + ". " + */ line);
		paraNode.touch();
		update.save(session, paraNode);
		paragraph.setLength(0);
		return true;
	}

	private boolean anyEpilogue(String line) {
		return line.startsWith("FIRST EPILOGUE") || //
				line.startsWith("SECOND EPILOGUE") || //
				line.startsWith("THIRD EPILOGUE") || //
				line.startsWith("FOURTH EPILOGUE");
	}

	private boolean processBook(String line) {
		if (line.startsWith("BOOK ") || anyEpilogue(line)) {
			globalBook++;
			if (globalBook > maxBooks)
				return false;
			addParagraph();

			curBook = create.createNode(session, root, NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, true);
			curBook.setContent(/* "B" + String.valueOf(globalBook) + ". " + */ line);
			curBook.touch();
			update.save(session, curBook);
			return true;
		}
		return false;
	}
}
