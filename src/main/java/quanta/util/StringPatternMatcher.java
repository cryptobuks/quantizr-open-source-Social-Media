/**
 * Regular expression pattern matching helper class.
 */
package quanta.util;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Some simple string pattern matching utilities.
 */
public class StringPatternMatcher {
	private ArrayList<String> patternList = new ArrayList<>();

	public StringPatternMatcher() {
	}

	public void addPattern(String searchPattern) {
		synchronized (patternList) {
			String search = searchPattern;
			search = search.trim();
			// search = "\\A" + search + "\\z";
			search = search.replace("*", ".*");
			// search = search.replace("?", ".*");
			// search = "(?i)" + search;

			patternList.add(search);
		}
	}

	public void clearPatterns() {
		synchronized (patternList) {
			patternList.clear();
		}
	}

	public boolean matches(String text) {
		synchronized (patternList) {
			for (String pattern : patternList) {
				if (Pattern.compile(pattern).matcher(text).matches()) {
					return true;
				}
			}
			return false;
		}
	}

	public void addListOfPatterns(String input) {
		if (input == null || input.length() == 0) return;

		StringTokenizer t = new StringTokenizer(input, ",", true);
		String token;
		while (t.hasMoreTokens()) {
			token = t.nextToken();
			if (!token.equals(",")) {
				addPattern(token);
			}
		}
	}
}
