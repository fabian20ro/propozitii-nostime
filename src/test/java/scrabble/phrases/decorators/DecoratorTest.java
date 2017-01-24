package scrabble.phrases.decorators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import scrabble.phrases.decorators.DexonlineLinkAdder;
import scrabble.phrases.providers.ISentenceProvider;

public class DecoratorTest {

	@Test
	public void test() {

		String expected = "<a href=\"https://dexonline.ro/definitie/ana\">Ana</a><div class=\"box\">"
				+ "<iframe src=\"https://dexonline.ro/definitie/ana\" width = \"480px\" height = \"800px\"></iframe></div><br/>"
				+ "<a href=\"https://dexonline.ro/definitie/are\">are</a><div class=\"box\">"
				+ "<iframe src=\"https://dexonline.ro/definitie/are\" width = \"480px\" height = \"800px\"></iframe></div> "
				+ "<a href=\"https://dexonline.ro/definitie/mere\">mere</a><div class=\"box\">"
				+ "<iframe src=\"https://dexonline.ro/definitie/mere\" width = \"480px\" height = \"800px\"></iframe></div>.";
		assertEquals(expected,
				new HtmlVerseBreaker(new DexonlineLinkAdder(new FirstSentenceLetterCapitalizer(new ISentenceProvider() {
					@Override
					public String getSentence() {
						return "ana / are mere.";
					}
				}))).getSentence());

	}

}
