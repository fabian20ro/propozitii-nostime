package scrabble.phrases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounType;
import scrabble.phrases.words.WordUtils;

/**
 * The Class Main.
 */
public class Main {

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {

		InputStream in = Main.class.getResourceAsStream("/words.txt");
		if (in == null) {
			in = new FileInputStream(new File("src/main/resources/words.txt"));
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
		WordDictionary dictionary = new WordParser().parse(reader);

		dictionary.addFilter(word -> word.getName().length() == 8);
		
		int count = 20;
		if (args.length > 0) {
			try {
				count = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				// nothing to do
			}
		}
		for (int i = 1; i <= count; i++) {
			String sentence = getNACombo(dictionary) + " " + dictionary.getRandomVerb().getName() + " " + getNACombo(dictionary)
					+ ".";
			sentence = WordUtils.capitalizeFirstLeter(sentence);
			System.out.println(i + ". " + sentence);
		}

		System.out.println("\n\nCuvinte neincluse:");
		for (int i = 1; i <= count; i++) {
			System.out.println(dictionary.getRandomUnknown());
		}
	}

	/**
	 * Gets the NA combo.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @return the NA combo
	 */
	private static String getNACombo(WordDictionary dictionary) {
		// TODO Auto-generated method stub
		Noun randomNoun = dictionary.getRandomNoun();
		Adjective randomAdjective = dictionary.getRandomAdjective();
		return randomNoun.getArticulatedForm() + " " + (NounType.FEMININE.equals(randomNoun.getGender())
				? randomAdjective.getFeminine() : randomAdjective.getName());
	}

}
