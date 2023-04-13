package quanta.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;

@Component
@Slf4j
public class EnglishDictionary extends ServiceBase {
	private static final HashSet<String> dictWords = new HashSet<>();
	private static final HashSet<String> stopWords = new HashSet<>();
	private static final HashSet<String> badWords = new HashSet<>();

	@EventListener
	public void handleContextRefresh(ContextRefreshedEvent event) {
		ServiceBase.init(event.getApplicationContext());
		log.debug("ContextRefreshedEvent");
		loadWords("classpath:public/data/english-dictionary.txt", dictWords);
		loadWords("classpath:public/data/stop-words.txt", stopWords);
		loadWords("classpath:public/data/bad-words.txt", badWords);
	}

	public void loadWords(String fileName, HashSet<String> words) {
		if (words.size() > 0)
			return;

		try {
			/*
			 * todo-1: Tip: Here's a shell script which starts with unsorted ununique 'words.txt' and processes
			 * it into unique sorted list in 'words-unique.txt'
			 */
			// sed 's/[[:blank:]]//g' words.txt > cleaned.txt
			// awk '!seen[$0]++' cleaned.txt | sort > words-unique.txt

			Resource resource = context.getResource(fileName);
			InputStream is = resource.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			try {
				String line;
				while ((line = in.readLine()) != null) {
					line = line.trim();
					if (line.length() > 0) {
						words.add(line.toLowerCase());
					}
				}
			} finally {
				StreamUtil.close(in);
			}
			log.debug(fileName + " Word Count=" + words.size());
		} catch (Exception ex) {
			// log and ignore.
			log.error("Failed to load " + fileName, ex);
		}
	}

	public void writeStringArrayToFile(ArrayList<String> list, String fileName) {
		BufferedOutputStream os = null;
		try {
			byte[] nl = "\n".getBytes(StandardCharsets.UTF_8);
			os = new BufferedOutputStream(new FileOutputStream(fileName));
			for (String s : list) {
				os.write(s.getBytes(StandardCharsets.UTF_8));
				os.write(nl);
			}
		} catch (Exception e) {
			log.error("Error writing file", e);
		} finally {
			StreamUtil.close(os);
		}
	}

	public boolean isStopWord(String word) {
		if (word == null)
			return true;
		return stopWords.contains(word.toLowerCase());
	}


	/*
	 * Tokenizes text (like a paragraph of text) to determine if it appears to be English.
	 * 
	 * Unfortunately this will currently kick out computer code as non-english, but it I have some ideas
	 * for how fix that by detecting an unusual high number of free floating '{' or '=' or lines ending
	 * in semicolon for example to detect and allow code.
	 * 
	 * example threshold=0.60f -> 60% english)
	 */
	public boolean isEnglish(String text) {
		if (text == null)
			return true;
		if (dictWords.size() == 0)
			throw new RuntimeException("called isEnglish before dictionary was loaded.");

		// log.debug("Checking english: " + text);
		int englishCount = 0;
		int unknownCount = 0;

		/*
		 * Counts all the 'words' in the text that consist purely of alphabet strings (letters) and returns
		 * true only of known English reach a threshold percentage.
		 */
		StringTokenizer tokens = new StringTokenizer(text, " \n\r\t.?!><", false);
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken().trim();

			if (XString.containsNonEnglish(token)) {
				unknownCount++;
				continue;
			}

			// only consider words that are all alpha characters
			if (!StringUtils.isAlpha(token) || token.length() < 4) {
				// log.debug(" ignoring: " + token);
				continue;
			}

			token = token.toLowerCase();

			switch (token) {
				case "span":
				case "div":
				case "html":
				case "img":
					continue;
				default:
					break;
			}

			// log.debug("tok: " + token);
			if (dictWords.contains(token)) {
				englishCount++;
				// log.debug(" isEnglish: " + token);
			} else {
				unknownCount++;
				// log.debug(" notEnglish: " + token);
			}
		}

		if (englishCount == 0 && unknownCount == 0)
			return true;

		float percent = (float) englishCount / (englishCount + unknownCount);
		// log.debug("eng=" + englishCount + " nonEng=" + unknownCount + " %=" + percent);
		return percent > 0.6f;
	}

	public boolean hasBadWords(String text) {
		if (badWords.size() == 0)
			throw new RuntimeException("called isBadWord before dictionary was loaded.");
		if (text == null)
			return false;

		StringTokenizer tokens = new StringTokenizer(text, " \n\r\t.,-;:\"'`!?()*#<>", false);
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken().trim().toLowerCase();
			if (badWords.contains(token)) {
				// log.debug("BadWord[" + token + "]");
				return true;
			}
		}
		return false;
	}
}
