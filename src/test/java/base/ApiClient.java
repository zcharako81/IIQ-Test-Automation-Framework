package base;



import io.restassured.RestAssured;

import io.restassured.response.Response;

import io.restassured.specification.RequestSpecification;

public class ApiClient {

    private static RequestSpecification baseRequest() {

        RequestSpecification request = RestAssured

                .given()

                .baseUri(ConfigManager.get("base.url"))

                .header("Content-Type", "application/json").log().all();;

        return AuthManager.applyAuth(request);

    }

    public static Response post(String endpoint, Object body) {

        return baseRequest()

                .body(body)

                .post(endpoint)

                .andReturn();

    }

    public static Response put(String endpoint, Object body) {

        return baseRequest()

                .body(body)

                .put(endpoint)

                .andReturn();

    }

    public static Response get(String endpoint) {

        return baseRequest()

                .get(endpoint)

                .andReturn();

    }

    public static Response patch(String endpoint, Object body) {

        return baseRequest()

                .body(body)

                .patch(endpoint)

                .andReturn();

    }

    public static Response delete(String endpoint) {

        return baseRequest()

                .delete(endpoint)

                .andReturn();

    }

}