package scrabble.phrases;

import ratpack.server.RatpackServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Word;
import scrabble.phrases.words.WordUtils;

/**
 * The Class Main.
 */
public class Main {

	/** The Constant DEFAULT_NUMBER_OF_PHRASES. */
	private static final int DEFAULT_NUMBER_OF_PHRASES = 40;

	/** The dictionary. */
	private WordDictionary dictionary;

	public void init() throws IOException {
		dictionary = getPopulatedDictionaryFromIncludedFile();
	}

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 * @throws Exception
	 *             the exception
	 */
	public static void main(String[] args) throws Exception {

		System.out.println("Arguments should be one of: phrases <2-5 words> ; haiku ; epigram");

		Main main = new Main();
		main.init();
		main.work(args);
		main.startRatpack();
	}

	private void startRatpack() throws Exception {
		RatpackServer
				.start(server -> server.handlers(chain -> chain.get(ctx -> ctx.render(this.getSentence(dictionary)))));
	}

	/**
	 * Work.
	 *
	 * @param args
	 *            the args
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private void work(String[] args) throws IOException {

		do {
			dictionary.clearFilters();
			final Noun noun = dictionary.getRandomNoun();
			dictionary.addFilter(new IWordFilter() {
				@Override
				public boolean accepts(Word word) {
					if (word instanceof Noun) {
						return WordUtils.computeSyllableNumber(((Noun) word).getArticulated()) == 5
								&& noun.getRhyme().equals(word.getRhyme());
					}
					if (word instanceof Adjective) {
						return word.getSyllables() == 3 + (noun.getGender() == NounGender.FEMININE ? 0 : 1);
					}
					return word.getSyllables() == 3;
				}
			});
		} while (dictionary.getRandomNoun() == null);

		int count = getNumberOfPhrasesToGenerate(args);
		for (int i = 1; i <= count; i++) {
			String sentence = getSentence(dictionary);
			sentence = WordUtils.capitalizeFirstLeter(sentence);
			System.out.println(i + ". " + sentence);
		}

		System.out.println("\n\nCuvinte neincluse:");
		for (int i = 1; i <= 10; i++) {
			System.out.println(dictionary.getRandomUnknown());
		}
	}

	/**
	 * Gets the sentence.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @return the sentence
	 */
	private String getSentence(WordDictionary dictionary) {
		return getNACombo(dictionary).replaceAll(" ", " / ") + " " + dictionary.getRandomVerb().getWord() + " / "
				+ dictionary.getRandomNoun().getArticulated() + ".";
	}

	/**
	 * Gets the number of phrases to generate.
	 *
	 * @param args
	 *            the args
	 * @return the number of phrases to generate
	 */
	private int getNumberOfPhrasesToGenerate(String[] args) {
		int count = DEFAULT_NUMBER_OF_PHRASES;
		if (args.length > 0) {
			try {
				count = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				// nothing to do
			}
		}
		return count;
	}

	/**
	 * Gets the populated dictionary from included file.
	 *
	 * @return the populated dictionary from included file
	 * @throws FileNotFoundException
	 *             the file not found exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public WordDictionary getPopulatedDictionaryFromIncludedFile() throws FileNotFoundException, IOException {
		BufferedReader reader = getWordStreamReader();
		WordDictionary dictionary = new WordParser().parse(reader);
		return dictionary;
	}

	/**
	 * Gets the word stream reader.
	 *
	 * @return the word stream reader
	 * @throws FileNotFoundException
	 *             the file not found exception
	 */
	private BufferedReader getWordStreamReader() throws FileNotFoundException {
		InputStream in = Main.class.getResourceAsStream("/words.txt");
		if (in == null) {
			in = new FileInputStream(new File("src/main/resources/words.txt"));
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
		return reader;
	}

	/**
	 * Gets the NA combo.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @return the NA combo
	 */
	private String getNACombo(WordDictionary dictionary) {
		// TODO Auto-generated method stub
		Noun randomNoun = dictionary.getRandomNoun();
		Adjective randomAdjective = dictionary.getRandomAdjective();
		return randomNoun.getArticulated() + " " + (NounGender.FEMININE.equals(randomNoun.getGender())
				? randomAdjective.getFeminine() : randomAdjective.getWord());
	}

}
