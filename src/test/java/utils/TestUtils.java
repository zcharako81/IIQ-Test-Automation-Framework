package utils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import base.ConfigManager;
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
}