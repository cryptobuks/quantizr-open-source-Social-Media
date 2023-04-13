package quanta.util;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.io.IOUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import quanta.exception.base.RuntimeEx;

/**
 * General string utilities.
 * 
 * todo-3: Look for ways to use this: Java 11 adds a few new methods to the String class: isBlank,
 * lines, strip, stripLeading, stripTrailing, and repeat.
 */
@Slf4j
public class XString {
	public static final ObjectMapper jsonMapper = new ObjectMapper();
	static {
		jsonMapper.setSerializationInclusion(Include.NON_NULL);
	}
	private static ObjectWriter jsonPrettyWriter = jsonMapper.writerWithDefaultPrettyPrinter();
	private static ObjectWriter jsonCompactWriter = jsonMapper.writer();

	public static String prettyPrint(Object obj) {
		if (obj == null)
			return "null";
		if (obj instanceof String) {
			return (String) obj;
		}

		try {
			return jsonPrettyWriter.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "";
		}
	}

	public static String compactPrint(Object obj) {
		if (obj == null)
			return "null";
		if (obj instanceof String) {
			return (String) obj;
		}

		try {
			return jsonCompactWriter.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "";
		}
	}

	public static String getStringFromStream(InputStream inputStream) {
		try {
			StringWriter writer = new StringWriter();
			String encoding = StandardCharsets.UTF_8.name();
			IOUtils.copy(inputStream, writer, encoding);
			return writer.toString();
		} catch (Exception e) {
			throw new RuntimeEx("getStringFromStream failed.", e);
		}
	}

	public static String lastNChars(String val, int chars) {
		if (val.length() > chars) {
			return val.substring(val.length() - chars);
		} else {
			return val;
		}
	}

	public static String repeatingTrimFromFront(String val, String prefix) {
		if (val == null)
			return null;
		int loopSafe = 0;
		while (++loopSafe < 1000) {
			int len = val.length();
			val = stripIfStartsWith(val.trim(), prefix);

			/* if string remained same length we're done */
			if (len == val.length()) {
				break;
			}
		}
		return val;
	}

	public static List<String> tokenizeWithDelims(String val, String delimiter) {
		if (val == null)
			return null;
		List<String> list = null;
		StringTokenizer t = new StringTokenizer(val, delimiter, true);
		while (t.hasMoreTokens()) {
			if (list == null) {
				list = new LinkedList<>();
			}
			list.add(t.nextToken());
		}
		return list;
	}

	public static List<String> tokenize(String val, String delimiter, boolean trim) {
		if (val == null)
			return null;
		List<String> list = null;
		StringTokenizer t = new StringTokenizer(val, delimiter, false);
		while (t.hasMoreTokens()) {
			if (list == null) {
				list = new LinkedList<>();
			}
			list.add(trim ? t.nextToken().trim() : t.nextToken());
		}
		return list;
	}

	public static HashSet<String> tokenizeToSet(String val, String delimiter, boolean trim) {
		HashSet<String> list = null;
		StringTokenizer t = new StringTokenizer(val, delimiter, false);
		while (t.hasMoreTokens()) {
			if (list == null) {
				list = new HashSet<>();
			}
			list.add(trim ? t.nextToken().trim() : t.nextToken());
		}
		return list;
	}

	/*
	 * Returns the heading level assuming 'val' contains text that starts with something like
	 * "# My Heading" (reurns 1), or "## My Heading" (returns 2), by returning the number of hash marks
	 * in the heading. Anything not a heading will return 0
	 */
	public static int getHeadingLevel(String val) {
		if (!val.startsWith("#")) {
			return 0;
		}
		int len = val.length();
		int idx = 0;
		char c = 0;
		/*
		 * we have 'len-2' here becasue in an example like "## a" this is a heading "a", and we don't need
		 * to try to iterate into the final " a" part so we know we should always iterate only out to two
		 * chars from the end of the string
		 */
		while (idx < len - 2 && (c = val.charAt(idx)) == '#') {
			idx++;

			// if we've counted the max number of headings levels, just point 'c' to the next char
			// bail out of looping
			if (idx >= 6) {
				c = val.charAt(idx);
				break;
			}
		}
		if (c != ' ')
			return 0;
		return idx;
	}

	public static boolean isMarkdownHeading(String val) {
		if (val == null)
			return false;
		int level = getHeadingLevel(val);
		return level >= 1 && level <= 6;
	}

	public static String trimToMaxLen(String val, int maxLen) {
		if (val == null)
			return null;
		if (val.length() <= maxLen)
			return val;
		return val.substring(0, maxLen - 1);
	}

	public static String getResourceAsString(ApplicationContext context, String resourceName) {
		InputStream is = null;
		String ret = null;
		resourceName = "classpath:" + resourceName; // "classpath:/public/export-includes/flexmark/html-template.html";
		try {
			Resource resource = context.getResource(resourceName);
			is = resource.getInputStream();
			ret = IOUtils.toString(is, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			throw new RuntimeEx("Unable to read resource: " + resourceName, e);
		} finally {
			StreamUtil.close(is);
		}
		return ret;
	}

	/* Truncates after delimiter including truncating the delimiter */
	public static String truncAfterFirst(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.indexOf(delim);
		if (idx != -1) {
			text = text.substring(0, idx);
		}
		return text;
	}

	public static String stripIfEndsWith(String val, String suffix) {
		if (val.endsWith(suffix)) {
			val = val.substring(0, val.length() - suffix.length());
		}
		return val;
	}

	public static String stripIfStartsWith(String val, String prefix) {
		if (val == null)
			return val;
		if (val.startsWith(prefix)) {
			val = val.substring(prefix.length());
		}
		return val;
	}

	public static String removeLastChar(String str) {
		return str.substring(0, str.length() - 1);
	}

	public static String truncAfterLast(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.lastIndexOf(delim);
		if (idx != -1) {
			text = text.substring(0, idx);
		}
		return text;
	}

	public static String parseAfterLast(String text, String delim) {
		if (text == null)
			return null;

		int idx = text.lastIndexOf(delim);
		if (idx != -1) {
			text = text.substring(idx + delim.length());
		}
		return text;
	}

	/*
	 * Ensures string containing val which is number is prepended with leading zeroes to make the string
	 * 'count' chars long. Using simplest inefficient algorithm for now. Can be done faster with one
	 * concat
	 */
	public static String addLeadingZeroes(String val, int count) {
		while (val.length() < count) {
			val = "0" + val;
		}
		return val;
	}

	/**
	 * input: abc--file.txt, -- output: file.txt
	 */
	public String truncBefore(String fileName, String delims) {
		if (fileName == null)
			return null;

		String ret = null;
		int idx = fileName.indexOf(delims);
		if (idx != -1) {
			ret = fileName.substring(idx + delims.length());
		} else {
			ret = fileName;
		}
		// log.debug("truncateBefore: input[" + fileName + "] output[" + ret + "]");
		return ret;
	}

	/**
	 * input: abc--file.txt, . output: abc--file
	 */
	public String truncAfter(String fileName, String delims) {
		if (fileName == null)
			return null;

		String ret = null;
		int idx = fileName.lastIndexOf(delims);
		if (idx != -1) {
			ret = fileName.substring(0, idx);
		} else {
			ret = fileName;
		}
		// log.debug("truncateAfter: input[" + fileName + "] output[" + ret + "]");
		return ret;
	}

	/**
	 * input: /home/clay/path/file.txt output: /home/clay/path
	 */
	public String getPathPart(String fileName) {
		if (fileName == null)
			return null;

		String pathPart = null;
		int idx = fileName.lastIndexOf(File.separatorChar);
		if (idx != -1) {
			pathPart = fileName.substring(0, idx);
		} else {
			pathPart = fileName;
		}
		// log.debug("Short name of [" + fileName + "] is [" + shortName + "]");
		return pathPart;
	}

	public static boolean containsNonEnglish(String s) {
		if (s == null || s.length() == 0)
			return false;
		for (int i = 0; i < s.length(); i++) {
			if ((int) s.charAt(i) >= 128)
				return true;
		}
		return false;
	}

	public static boolean containsChinese(String s) {
		// This is specifically for Chinese
		// return s.codePoints().anyMatch(codepoint -> Character.UnicodeScript.of(codepoint) ==
		// Character.UnicodeScript.HAN);

		// This is more general.
		return s.codePoints().anyMatch(codepoint -> Character.isIdeographic(codepoint));
	}

	public static boolean containsRussian(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.UnicodeBlock.of(s.charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
				return true;
			}
		}
		return false;
	}
}
