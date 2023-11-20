package com.kinnovatio;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class NegotiateResourceTest {

    @Test
    public void testNegotiateEndpoint() {
        given()
          .when().get("/negotiate")
          .then()
             .statusCode(200)
             .body(containsString("\"ConnectionToken\": \"blahblah\""));
    }

}