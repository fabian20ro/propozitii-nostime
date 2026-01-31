package scrabble.phrases;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for PhraseResource REST API.
 */
@QuarkusTest
class PhraseResourceTest {

    @Test
    void shouldReturnHaiku() {
        given()
            .when().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldReturnCouplet() {
        given()
            .when().get("/api/couplet")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldReturnComparison() {
        given()
            .when().get("/api/comparison")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldReturnDefinition() {
        given()
            .when().get("/api/definition")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldReturnTautogram() {
        given()
            .when().get("/api/tautogram")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldReturnMirror() {
        given()
            .when().get("/api/mirror")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue())
            .body("sentence", containsString("<a href="));
    }

    @Test
    void shouldResetProviders() {
        given()
            .when().post("/api/reset")
            .then()
            .statusCode(200);

        // Verify we can still generate after reset
        given()
            .when().get("/api/haiku")
            .then()
            .statusCode(200)
            .body("sentence", notNullValue());
    }
}
