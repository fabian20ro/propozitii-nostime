package scrabble.phrases.words;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for Adjective record: feminine form derivation rules.
 */
class AdjectiveTest {

    @ParameterizedTest
    @CsvSource({
        "pitoresc, pitorească",
        "citeț, citeață",
        "bor, boare",
        "frumos, frumoasă",
        "zglobiu, zglobie",
        "stângaci, stângace",
        "acru, acră",
        "alb, albă",
        "verde, verde",
        "maro, maro",
        "gri, gri",
    })
    void shouldDeriveFeminineForm(String masculine, String expectedFeminine) {
        Adjective adj = new Adjective(masculine);
        assertThat(adj.feminine()).isEqualTo(expectedFeminine);
    }

    @Test
    void shouldComputeSyllablesAndRhyme() {
        Adjective adj = new Adjective("frumos");
        assertThat(adj.syllables()).isEqualTo(2);
        assertThat(adj.rhyme()).isEqualTo("mos");
    }

    @Test
    void shouldRejectNullWord() {
        assertThatThrownBy(() -> new Adjective(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldProvideRecordAccessors() {
        Adjective adj = new Adjective("mare");
        assertThat(adj.word()).isEqualTo("mare");
        assertThat(adj.getWord()).isEqualTo("mare");
        assertThat(adj.getFeminine()).isEqualTo(adj.feminine());
    }
}
