package utils;

import org.testng.Assert;

import io.restassured.response.Response;

public class ResponseValidator {

    public static void assertSuccess(Response response) {

        int status = response.statusCode();

        Assert.assertTrue(

            status >= 200 && status < 300,

            "Unexpected status: " + status

        );

    }

}