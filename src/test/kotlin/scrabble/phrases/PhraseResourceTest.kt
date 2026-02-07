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
    }

    @Test
    fun shouldReturnCouplet() {
        given()
            .`when`().get("/api/couplet")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
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
    }

    @Test
    fun shouldReturnMirror() {
        given()
            .`when`().get("/api/mirror")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="))
    }

    @Test
    fun shouldReturnAllSentences() {
        given()
            .`when`().get("/api/all")
            .then()
            .statusCode(200)
            .body("haiku", notNullValue())
            .body("couplet", notNullValue())
            .body("comparison", notNullValue())
            .body("definition", notNullValue())
            .body("tautogram", notNullValue())
            .body("mirror", notNullValue())
    }

    @Test
    fun shouldClampRarityQueryParam() {
        given()
            .queryParam("rarity", 0)
            .`when`().get("/api/all")
            .then()
            .statusCode(200)
            .body("haiku", notNullValue())
            .body("couplet", notNullValue())
            .body("comparison", notNullValue())
            .body("definition", notNullValue())
            .body("tautogram", notNullValue())
            .body("mirror", notNullValue())

        given()
            .queryParam("rarity", 6)
            .`when`().get("/api/all")
            .then()
            .statusCode(200)
            .body("haiku", notNullValue())
            .body("couplet", notNullValue())
            .body("comparison", notNullValue())
            .body("definition", notNullValue())
            .body("tautogram", notNullValue())
            .body("mirror", notNullValue())
    }

    @Test
    fun shouldReturnPlaceholderWhenConstraintsAreImpossible() {
        given()
            .queryParam("rarity", 1)
            .`when`().get("/api/couplet")
            .then()
            .statusCode(200)
            .body("sentence", equalTo(PhraseResource.UNSATISFIABLE_PLACEHOLDER))
    }
}
