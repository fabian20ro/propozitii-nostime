package scrabble.phrases.words;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * The Class WordUtilsTest.
 */
public class WordUtilsTest {

	/**
	 * Test.
	 */
	@Test
	public void test() {
		assertEquals("Aloha", WordUtils.capitalizeFirstLeter("aloha"));
		assertEquals("Aloha", WordUtils.capitalizeFirstLeter("Aloha"));
		assertEquals("țâgâlirea", WordUtils.capitalizeFirstLeter("țâgâlirea"));
	}

}
