package scrabble.phrases.words;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for Noun record: articulation rules and computed properties.
 */
class NounTest {

    @ParameterizedTest
    @CsvSource({
        "acar, M, acarul",
        "maestru, M, maestrul",
        "codru, M, codrul",
        "staul, N, staulul",
        "pod, N, podul",
    })
    void shouldArticulateMasculineAndNeutral(String word, String gender, String expected) {
        Noun noun = new Noun(word, NounGender.valueOf(gender));
        assertThat(noun.articulated()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "fată, F, fata",
        "macara, F, macaraua",
        "ploaie, F, ploaia",
        "rodie, F, rodia",
    })
    void shouldArticulateFeminine(String word, String gender, String expected) {
        Noun noun = new Noun(word, NounGender.valueOf(gender));
        assertThat(noun.articulated()).isEqualTo(expected);
    }

    @Test
    void shouldComputeSyllablesAndRhyme() {
        Noun noun = new Noun("macara", NounGender.F);
        assertThat(noun.syllables()).isEqualTo(3);
        assertThat(noun.rhyme()).isEqualTo("ara");
    }

    @Test
    void shouldRejectNullWord() {
        assertThatThrownBy(() -> new Noun(null, NounGender.M))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullGender() {
        assertThatThrownBy(() -> new Noun("test", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldProvideRecordAccessors() {
        Noun noun = new Noun("carte", NounGender.F);
        assertThat(noun.word()).isEqualTo("carte");
        assertThat(noun.gender()).isEqualTo(NounGender.F);
        assertThat(noun.getWord()).isEqualTo("carte");
        assertThat(noun.getGender()).isEqualTo(NounGender.F);
        assertThat(noun.getArticulated()).isEqualTo(noun.articulated());
    }
}
