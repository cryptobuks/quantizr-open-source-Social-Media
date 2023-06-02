package quanta.util;

import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;

/**
 * Mime-Type (content type) utilities
 */
@Component
public class MimeUtil extends ServiceBase {

	public boolean isTextTypeFileName(String fileName) {
		if (!fileName.contains("."))
			return false;

		String ext = XString.parseAfterLast(fileName, ".");

		// todo-2: will be getting these from a properties file eventually
		if (ext.equalsIgnoreCase("txt") || //
				ext.equalsIgnoreCase("md") || //
				ext.equalsIgnoreCase("json")) {
			return true;
		}
		return false;
	}

	public boolean isHtmlTypeFileName(String fileName) {
		if (!fileName.contains("."))
			return false;

		String ext = XString.parseAfterLast(fileName, ".");

		// todo-2: will be getting these from a properties file eventually
		if (ext.equalsIgnoreCase("htm") || //
				ext.equalsIgnoreCase("html")) {
			return true;
		}
		return false;
	}

	public boolean isJsonFileType(String fileName) {
		if (!fileName.contains("."))
			return false;
		if (fileName.toLowerCase().endsWith(".json.txt"))
			return true;
		String ext = XString.parseAfterLast(fileName, ".");
		return ext.equalsIgnoreCase("json");
	}
}
