package scrabble.phrases.decorators;

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
		StringBuffer buffer = new StringBuffer();
		int wordIndex = 0, spaceIndex = 0;
		if (spaces.length > words.length) {
			buffer = buffer.append(spaces[spaceIndex++]);
		}
		while (wordIndex < words.length && spaceIndex < spaces.length) {
			buffer = buffer.append(addHref(words[wordIndex++]));
			buffer = buffer.append(spaces[spaceIndex++]);
		}
		if (wordIndex < words.length) {
			addHref(words[wordIndex]);
		}
		return buffer.toString();
	}

	private String addHref(String word) {
		String url = HTTPS_DEXONLINE_RO_DEFINITIE + word.toLowerCase();
		return "<a href=\"" + url + "\">" + word + "</a><div class=\"box\"><iframe src=\"" + url
				+ "\" width = \"480px\" height = \"800px\"></iframe></div>";
	}

}
