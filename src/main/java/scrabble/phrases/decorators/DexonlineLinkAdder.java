package scrabble.phrases.decorators;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import scrabble.phrases.providers.ISentenceProvider;

public class DexonlineLinkAdder implements ISentenceProvider {

	private static final String HTTPS_DEXONLINE_RO_DEFINITIE = "https://dexonline.ro/definitie/";
	private ISentenceProvider provider;

	public DexonlineLinkAdder(ISentenceProvider provider) {
		this.provider = provider;
	}

	@Override
	public String getSentence() {

		String sentence = provider.getSentence();
		String[] words = sentence.split("[^\\p{L}]+");
		String[] spaces = sentence.split("\\p{L}+");
		StringBuilder buffer = new StringBuilder();
		int wordIndex = 0, spaceIndex = 0;
		if (spaces.length > words.length) {
			buffer.append(spaces[spaceIndex++]);
		}
		while (wordIndex < words.length && spaceIndex < spaces.length) {
			buffer.append(addHref(words[wordIndex++]));
			buffer.append(spaces[spaceIndex++]);
		}
		if (wordIndex < words.length) {
			buffer.append(addHref(words[wordIndex]));
		}
		return buffer.toString();
	}

	private String addHref(String word) {
		String encodedWord = URLEncoder.encode(word.toLowerCase(), StandardCharsets.UTF_8);
		String url = HTTPS_DEXONLINE_RO_DEFINITIE + encodedWord;
		String escapedWord = escapeHtml(word);
		return "<a href=\"" + url + "\">" + escapedWord + "</a><div class=\"box\"><iframe src=\"" + url
				+ "\" width = \"480px\" height = \"800px\"></iframe></div>";
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

}
