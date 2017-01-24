package scrabble.phrases.decorators;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import scrabble.phrases.decorators.DexonlineLinkAdder;
import scrabble.phrases.providers.ISentenceProvider;

public class DecoratorTest {

	@Test
	public void test() {

		String expected = "<a href=\"https://dexonline.ro/definitie/ana\">Ana</a> / "
				+ "<a href=\"https://dexonline.ro/definitie/are\">are</a> "
				+ "<a href=\"https://dexonline.ro/definitie/mere\">mere</a>.";
		assertEquals(new DexonlineLinkAdder(new ISentenceProvider() {
			
			@Override
			public String getSentence() {
				return "Ana / are mere.";
			}
		}).getSentence(), expected);
		
	}

}
