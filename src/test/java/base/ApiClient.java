package base;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class ApiClient {

    private static RequestSpecification baseRequest() {

        RequestSpecification request = RestAssured
                .given()
                .baseUri(ConfigManager.get("base.url"))
                .header("Content-Type", "application/json");

        if (ConfigManager.isLoggingEnabled()) {
            request = request.log().all();
        }

        return AuthManager.applyAuth(request);
    }

    private static Response logResponse(Response response) {
        if (ConfigManager.isLoggingEnabled()) {
            response.prettyPrint();
        }
        return response;
    }

    public static Response post(String endpoint, Object body) {

        return logResponse(baseRequest()
                .body(body)
                .post(endpoint)
                .andReturn());
    }

    public static Response put(String endpoint, Object body) {

        return logResponse(baseRequest()
                .body(body)
                .put(endpoint)
                .andReturn());
    }

    public static Response get(String endpoint) {

        return logResponse(baseRequest()
                .get(endpoint)
                .andReturn());
    }

    public static Response patch(String endpoint, Object body) {

        return logResponse(baseRequest()
                .body(body)
                .patch(endpoint)
                .andReturn());
    }

    public static Response delete(String endpoint) {

        return logResponse(baseRequest()
                .delete(endpoint)
                .andReturn());
    }
}
