package utils;

import org.testng.Assert;
import org.testng.asserts.SoftAssert;

import io.restassured.response.Response;

/**
 * Validates HTTP responses from SCIM API calls.
 * <p>
 * Provides both hard-assert and soft-assert variants so callers can
 * choose between fail-fast (hard) and collect-all-failures (soft) patterns.
 */
public class ResponseValidator {

    /**
     * Hard assert: throws immediately if status is not 2xx.
     */
    public static void assertSuccess(Response response) {
        int status = response.statusCode();
        Assert.assertTrue(
                status >= 200 && status < 300,
                "Unexpected status: " + status + " — body: " + response.getBody().prettyPrint()
        );
    }

    /**
     * Hard assert with a context message.
     */
    public static void assertSuccess(Response response, String context) {
        int status = response.statusCode();
        Assert.assertTrue(
                status >= 200 && status < 300,
                context + " — unexpected status: " + status + " — body: " + response.getBody().prettyPrint()
        );
    }

    /**
     * Soft assert: collects failure via SoftAssert without throwing immediately.
     */
    public static void assertSuccess(Response response, SoftAssert softAssert, String context) {
        int status = response.statusCode();
        softAssert.assertTrue(
                status >= 200 && status < 300,
                context + " — unexpected status: " + status
        );
    }

    /**
     * Asserts the response has a specific expected status code (hard assert).
     */
    public static void assertStatus(Response response, int expectedStatus) {
        int actual = response.statusCode();
        Assert.assertEquals(actual, expectedStatus,
                "Expected status " + expectedStatus + " but got " + actual
                        + " — body: " + response.getBody().prettyPrint());
    }

    /**
     * Asserts the response has a specific expected status code (soft assert).
     */
    public static void assertStatus(Response response, int expectedStatus,
                                    SoftAssert softAssert, String context) {
        int actual = response.statusCode();
        softAssert.assertEquals(actual, expectedStatus,
                context + " — expected status " + expectedStatus + " but got " + actual);
    }
}
