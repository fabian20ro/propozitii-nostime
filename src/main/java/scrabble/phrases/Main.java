package scrabble.phrases;

import ratpack.handlebars.internal.HandlebarsTemplateRenderer;
import ratpack.server.RatpackServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import scrabble.phrases.decorators.DexonlineLinkAdder;
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.providers.HaikuProvider;
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

	private HaikuProvider haiku;

	public void init() throws IOException {
		dictionary = getPopulatedDictionaryFromIncludedFile();

		haiku = new HaikuProvider(new WordDictionary(dictionary));
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
		RatpackServer.start(server -> server.handlers(chain -> chain.get(ctx -> ctx.render(haiku.getSentence()))));
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

		int count = getNumberOfPhrasesToGenerate(args);
		for (int i = 1; i <= count; i++) {
			System.out.println(i + ". " + new FirstSentenceLetterCapitalizer(haiku).getSentence());
		}

		System.out.println("\n\nCuvinte neincluse:");
		for (int i = 1; i <= 10; i++) {
			System.out.println(dictionary.getRandomUnknown());
		}
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

}
