package utils;

import java.time.Duration;
import java.util.function.Supplier;

import org.testng.Assert;

import base.ConfigManager;
import io.restassured.response.Response;
import services.WorkflowService;

public class TestUtils {

    public static void waitForCondition(Supplier<Boolean> condition,
                                        int timeoutSeconds,
                                        int pollMillis) {
        long end = System.currentTimeMillis()
                + Duration.ofSeconds(timeoutSeconds).toMillis();

        while (System.currentTimeMillis() < end) {
            try {
                if (condition.get()) return;
                Thread.sleep(pollMillis);
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("Condition not met within timeout");
    }
    
    public static void waitForWorkflowCompletion(
            WorkflowService service,
            String workflowId,
            int timeoutSeconds,
            int pollMillis) {

        waitForCondition(() -> {

            var response = service.getWorkflow(workflowId);

            if (response.statusCode() != 200) return false;

            String state = response.jsonPath().getString("completionStatus");

            return "Success".equalsIgnoreCase(state)
                || "Error".equalsIgnoreCase(state)
                || "TempError".equalsIgnoreCase(state)
                || "Terminated".equalsIgnoreCase(state)
                || "Warning".equalsIgnoreCase(state);

        }, timeoutSeconds, pollMillis);
    }

    /** Read default timeout in seconds from {@code wait.timeout.seconds} in config.properties. */
    public static int waitTimeout() {
        return Integer.parseInt(ConfigManager.get("wait.timeout.seconds"));
    }

    /** Read default poll interval in ms from {@code wait.poll.interval.ms} in config.properties. */
    public static int waitPoll() {
        return Integer.parseInt(ConfigManager.get("wait.poll.interval.ms"));
    }

    /** Read aggregation poll interval in ms from {@code wait.aggregation.poll.interval.ms}. */
    public static int aggregationPoll() {
        return Integer.parseInt(ConfigManager.get("wait.aggregation.poll.interval.ms"));
    }

    /** Asserts a string attribute only if the property key exists. Skips silently if missing. */
    public static void verifyStringAttr(Response r, String propKey, String jsonPath, String suffix) {
        String expected = ConfigManager.getOptional(propKey);
        if (expected == null) return;
        String actual = r.jsonPath().getString(jsonPath);
        Assert.assertEquals(actual, expected.replace("{suffix}", suffix), "Mismatch: " + propKey);
    }

    /** Asserts a boolean attribute only if the property key exists. Skips silently if missing. */
    public static void verifyBooleanAttr(Response r, String propKey, String jsonPath) {
        String expected = ConfigManager.getOptional(propKey);
        if (expected == null) return;
        Boolean actual = r.jsonPath().getBoolean(jsonPath);
        Assert.assertEquals(actual, Boolean.valueOf(expected), "Mismatch: " + propKey);
    }
}