package scrabble.phrases.decorators;

import scrabble.phrases.providers.ISentenceProvider;
import scrabble.phrases.words.WordUtils;

public class FirstSentenceLetterCapitalizer implements ISentenceProvider {

	private ISentenceProvider provider;

	public FirstSentenceLetterCapitalizer(ISentenceProvider provider) {
		this.provider = provider;
	}

	@Override
	public String getSentence() {
		return WordUtils.capitalizeFirstLetter(provider.getSentence().trim());
	}
	
	
}
