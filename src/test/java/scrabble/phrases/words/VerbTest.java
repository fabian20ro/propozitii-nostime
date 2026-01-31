package scrabble.phrases.words;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for Verb record.
 */
class VerbTest {

    @Test
    void shouldComputeSyllablesAndRhyme() {
        Verb verb = new Verb("aleargă");
        assertThat(verb.syllables()).isEqualTo(3);
        assertThat(verb.rhyme()).isEqualTo("rgă");
    }

    @Test
    void shouldRejectNullWord() {
        assertThatThrownBy(() -> new Verb(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldProvideRecordAccessors() {
        Verb verb = new Verb("merge");
        assertThat(verb.word()).isEqualTo("merge");
        assertThat(verb.getWord()).isEqualTo("merge");
        assertThat(verb.getSyllables()).isEqualTo(verb.syllables());
        assertThat(verb.getRhyme()).isEqualTo(verb.rhyme());
    }
}
