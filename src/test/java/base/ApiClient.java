package base;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class ApiClient {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;   // 10s
    private static final int DEFAULT_READ_TIMEOUT = 30000;      // 30s
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000;    // 30s

    static {
        int connectTimeout = parseIntOrDefault("connect.timeout.ms", DEFAULT_CONNECT_TIMEOUT);
        int readTimeout = parseIntOrDefault("read.timeout.ms", DEFAULT_READ_TIMEOUT);
        int socketTimeout = parseIntOrDefault("socket.timeout.ms", DEFAULT_SOCKET_TIMEOUT);

        RestAssured.config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", connectTimeout)
                        .setParam("http.socket.timeout", socketTimeout)
                        .setParam("http.connection-manager.timeout", (long) connectTimeout));
    }

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

    private static int parseIntOrDefault(String key, int defaultValue) {
        String val = ConfigManager.getOptional(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return defaultValue;
    }
}
