package com.shark.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.util.UUID;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthFlowE2ETest {

    private static String BASE_URL = "http://localhost:8080";
    private static String username;
    private static String email;
    private static String password = "SuperSecretPassword123!";
    private static String jwtToken;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = BASE_URL;
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        username = "e2e_user_" + uniqueSuffix;
        email = "e2e_" + uniqueSuffix + "@test.com";
    }

    @Test
    @Order(1)
    public void testHealthCheck() {
        given()
            .when().get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP")); // Spring Actuator usa "UP"
    }

    @Test
    @Order(2)
    public void testRegisterSuccess() {
        Map<String, String> body = Map.of(
            "username", username,
            "email", email,
            "password", password
        );

        Response response = given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/auth/register")
            .then()
            .statusCode(201)
            .body("token", notNullValue())
            .body("username", equalTo(username))
            .extract().response();
            
        jwtToken = response.path("token");
    }

    @Test
    @Order(3)
    public void testLoginSuccess() {
        Map<String, String> body = Map.of(
            "email", email,
            "password", password
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("username", equalTo(username));
    }

    @Test
    @Order(4)
    public void testWaitAndCheckProfile() throws InterruptedException {
        // Wait 3 seconds to allow RabbitMQ event to propagate
        Thread.sleep(3000);

        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when().get("/api/profiles/me")
            .then()
            .statusCode(200)
            .body("sharkName", equalTo(username))
            .body("colorHex", equalTo("#00D2FF"))
            .body("level", equalTo(1))
            .body("experience", equalTo(0))
            .body("totalScore", equalTo(0));
    }

    @Test
    @Order(5)
    public void testUpdateColor() {
        Map<String, String> body = Map.of(
            "colorHex", "#FF0000"
        );

        given()
            .header("Authorization", "Bearer " + jwtToken)
            .contentType(ContentType.JSON)
            .body(body)
            .when().patch("/api/profiles/me/color")
            .then()
            .statusCode(200)
            .body("message", containsString("Color actualizado"));

        // Verify update
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when().get("/api/profiles/me")
            .then()
            .statusCode(200)
            .body("colorHex", equalTo("#FF0000"));
    }

    @Test
    @Order(6)
    public void testProfileWithoutToken_Returns401() {
        given()
            .when().get("/api/profiles/me")
            .then()
            .statusCode(401);
    }
}
