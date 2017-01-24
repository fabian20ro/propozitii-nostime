package scrabble.phrases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import scrabble.phrases.decorators.DexonlineLinkAdder;
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer;
import scrabble.phrases.decorators.HtmlVerseBreaker;
import scrabble.phrases.providers.FiveWordSentenceProvider;
import scrabble.phrases.providers.HaikuProvider;
import spark.ModelAndView;
import spark.Spark;
import spark.template.handlebars.HandlebarsTemplateEngine;

/**
 * The Class Main.
 */
public class Main {

	/** The Constant DEFAULT_NUMBER_OF_PHRASES. */
	private static final int DEFAULT_NUMBER_OF_PHRASES = 40;

	/** The dictionary. */
	private WordDictionary dictionary;

	private HaikuProvider haiku;
	private FiveWordSentenceProvider fiveWord;

	public void init() throws IOException {
		dictionary = getPopulatedDictionaryFromIncludedFile();

		haiku = new HaikuProvider(new WordDictionary(dictionary));
		fiveWord = new FiveWordSentenceProvider(new WordDictionary(dictionary));
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
		main.startServer();
	}

	private void startServer() throws Exception {
		Spark.port(getHerokuAssignedPort());
		Spark.staticFileLocation("/public");
		Spark.get("/", "text/html", (request, response) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("test1", new HtmlVerseBreaker(new DexonlineLinkAdder(new FirstSentenceLetterCapitalizer(haiku))).getSentence());
            model.put("test2", new DexonlineLinkAdder(new FirstSentenceLetterCapitalizer(fiveWord)).getSentence());
            return new ModelAndView(model, "index.hbs"); // located in resources/templates
        }, new HandlebarsTemplateEngine());
	}

	//needed for heroku
	static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 5050; //return default port if heroku-port isn't set (i.e. on localhost)
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
