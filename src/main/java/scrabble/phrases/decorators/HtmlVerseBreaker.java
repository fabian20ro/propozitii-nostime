package scrabble.phrases.decorators;

import scrabble.phrases.providers.ISentenceProvider;

public class HtmlVerseBreaker implements ISentenceProvider {

	private ISentenceProvider provider;

	public HtmlVerseBreaker(ISentenceProvider provider) {
		this.provider = provider;
	}

	@Override
	public String getSentence() {
		return provider.getSentence().replace(" / ", "<br/>");
	}
	
	
}
