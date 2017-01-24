package scrabble.phrases.decorators;

import scrabble.phrases.providers.ISentenceProvider;

public class DexonlineLinkAdder implements ISentenceProvider {

	private ISentenceProvider provider;

	public DexonlineLinkAdder(ISentenceProvider provider) {
		this.provider = provider;
	}

	@Override
	public String getSentence() {
		
		String sentence = provider.getSentence();
		String[] words = sentence.split("[^\\w]+");
		String[] spaces = sentence.split("\\w+");
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

	private String addHref(String string) {
		return "<a href=\"https://dexonline.ro/definitie/" + string.toLowerCase() + "\">" + string + "</a>";
	}
	
}
