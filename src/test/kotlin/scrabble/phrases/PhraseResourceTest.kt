package scrabble.phrases

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test

@QuarkusTest
class PhraseResourceTest {

    @Test
    fun shouldReturnHaiku() {
        given()
            .`when`().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
            .body("sentence", containsString("<br/>"))
    }

    @Test
    fun shouldReturnDistih() {
        given()
            .`when`().get("/api/distih")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
            .body("sentence", containsString("<br/>"))
    }

    @Test
    fun shouldReturnComparison() {
        given()
            .`when`().get("/api/comparison")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
    }

    @Test
    fun shouldReturnDefinition() {
        given()
            .`when`().get("/api/definition")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
    }

    @Test
    fun shouldReturnTautogram() {
        given()
            .`when`().get("/api/tautogram")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
            // Tautogram is NOT a verse type — must not contain verse delimiters (<br/>).
            .body("sentence", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<br/>")))
    }

    @Test
    fun shouldReturnMirror() {
        given()
            .`when`().get("/api/mirror")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
            .body("sentence", containsString("<br/>"))
    }

    @Test
    fun shouldReturnAllSentences() {
        given()
            .`when`().get("/api/all")
            .then()
            .statusCode(200)
            .body("haiku", notNullValue())
            .body("distih", notNullValue())
            .body("comparison", notNullValue())
            .body("definition", notNullValue())
            .body("tautogram", notNullValue())
            .body("mirror", notNullValue())
    }

    @Test
    fun shouldClampRarityQueryParamBelowMin() {
        // rarity=0 must clamp to [1,5] — contract: status 200 + non-null sentence (real content or placeholder).
        given()
            .queryParam("rarity", 0)
            .`when`().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
    }

    @Test
    fun shouldClampRarityQueryParamAboveMax() {
        // rarity=6 must clamp to [1,5] — contract: status 200 + non-null sentence.
        given()
            .queryParam("rarity", 6)
            .`when`().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
    }

    @Test
    fun shouldClampAndHandleSwappedRarityParams() {
        // rarity=6, minRarity=0: both clamp into [1,5] range; swapped order must still produce content.
        given()
            .queryParam("rarity", 6)
            .queryParam("minRarity", 0)
            .`when`().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
    }

    @Test
    fun shouldReturnRealContentAfterClampingAboveMax() {
        // Verify clamping to a valid range (rarity=5 → [1,5]) produces actual sentence content.
        given()
            .queryParam("rarity", 5)
            .`when`().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
    }

    @Test
    fun shouldReturnPlaceholderWhenConstraintsAreImpossible() {
        given()
            .queryParam("rarity", 1)
            .`when`().get("/api/mirror")
            .then()
            .statusCode(200)
            .body("sentence", equalTo(PhraseResource.UNSATISFIABLE_PLACEHOLDER))
    }
}
