package base;

import io.restassured.specification.RequestSpecification;

public class AuthManager {

    public static RequestSpecification applyAuth(RequestSpecification request) {

        String authType = ConfigManager.get("auth.type");

        if ("basic".equalsIgnoreCase(authType)) {

            return request.auth().preemptive().basic(

                    ConfigManager.get("username"),

                    ConfigManager.get("password"));

        }

        // Future: OAuth / Session

        return request;

    }

}