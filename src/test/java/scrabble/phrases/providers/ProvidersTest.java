package scrabble.phrases.providers;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;

import scrabble.phrases.Main;

public class ProvidersTest {

	@Test
	public void test() throws IOException {
		assertNotNull(new HaikuProvider(new Main().getPopulatedDictionaryFromIncludedFile()).getSentence());
	}

}
