package scrabble.phrases;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * The Class WordParser.
 */
public class WordParser {

	/**
	 * Parses the.
	 *
	 * @param reader
	 *            the reader
	 * @return the word dictionary
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public WordDictionary parse(BufferedReader reader) throws IOException {
		String line;
		WordDictionary dictionary = new WordDictionary(8, 20);
		while ((line = reader.readLine()) != null) {
			String[] pieces = line.split("\\s+");
			if (pieces.length >= 2) {
				dictionary.addWord(pieces[0], pieces[1]);
			}
		}
		return dictionary;
	}

}
