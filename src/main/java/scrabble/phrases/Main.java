package scrabble.phrases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.WordUtils;

/**
 * The Class Main.
 */
public class Main {

	private static final int DEFAULT_NUMBER_OF_PHRASES = 40;

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {

		System.out.println("Arguments should be one of: phrases <2-5 words> ; haiku ; epigram");

		new Main().work(args);
		
	}

	private void work(String[] args) throws IOException {
		WordDictionary dictionary = getPopulatedDictionaryFromIncludedFile();

		dictionary.addFilter(word -> word.getWord().length() == 8);
		dictionary
				.addFilter(word -> word instanceof Noun ? NounGender.FEMININE.equals(((Noun) word).getGender()) : true);

		int count = getNumberOfPhrasesToGenerate(args);
		for (int i = 1; i <= count; i++) {
			String sentence = getNACombo(dictionary) + " " + dictionary.getRandomVerb().getWord() + " "
					+ getNACombo(dictionary) + ".";
			sentence = WordUtils.capitalizeFirstLeter(sentence);
			System.out.println(i + ". " + sentence);
		}

		System.out.println("\n\nCuvinte neincluse:");
		for (int i = 1; i <= 10; i++) {
			System.out.println(dictionary.getRandomUnknown());
		}
	}

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

	public WordDictionary getPopulatedDictionaryFromIncludedFile() throws FileNotFoundException, IOException {
		BufferedReader reader = getWordStreamReader();
		WordDictionary dictionary = new WordParser().parse(reader);
		return dictionary;
	}

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
