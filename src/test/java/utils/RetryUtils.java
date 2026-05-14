package utils;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.testng.Reporter;

import base.ConfigManager;
import io.restassured.response.Response;

/**
 * Utility for retrying operations that may experience transient failures.
 * <p>
 * Supports configurable max attempts, initial delay, and exponential backoff.
 * Configured via {@code config.properties}:
 * <ul>
 *   <li>{@code retry.max.attempts} — default 3</li>
 *   <li>{@code retry.initial.delay.ms} — default 1000</li>
 *   <li>{@code retry.backoff.multiplier} — default 2.0</li>
 * </ul>
 */
public class RetryUtils {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double backoffMultiplier;

    public RetryUtils() {
        this(
            parseOrDefault("retry.max.attempts", DEFAULT_MAX_ATTEMPTS),
            parseOrDefault("retry.initial.delay.ms", DEFAULT_INITIAL_DELAY_MS),
            parseOrDefaultDouble("retry.backoff.multiplier", DEFAULT_BACKOFF_MULTIPLIER)
        );
    }

    public RetryUtils(int maxAttempts, long initialDelayMs, double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * Executes the given {@code action} up to {@code maxAttempts} times,
     * retrying if the result does not satisfy {@code successCondition} or
     * if a {@link RetryableException} is thrown.
     *
     * @param action           the operation to retry
     * @param successCondition predicate that tests whether the response is acceptable
     * @param context          description for logging (e.g. "POST /Users")
     * @return the first response that satisfies {@code successCondition}
     * @throws RetryableException if all attempts are exhausted
     */
    public Response executeWithRetry(Callable<Response> action,
                                     Predicate<Response> successCondition,
                                     String context) {
        long delay = initialDelayMs;
        Response lastResponse = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response response = action.call();
                lastResponse = response;

                if (successCondition.test(response)) {
                    if (attempt > 1) {
                        Reporter.log("    [retry] " + context + " succeeded on attempt " + attempt);
                    }
                    return response;
                }

                Reporter.log("    [retry] " + context + " attempt " + attempt
                        + " returned status " + response.statusCode()
                        + (attempt < maxAttempts ? ", retrying in " + delay + "ms..." : ""));

            } catch (Exception e) {
                Reporter.log("    [retry] " + context + " attempt " + attempt
                        + " threw: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + (attempt < maxAttempts ? ", retrying in " + delay + "ms..." : ""));
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryableException("Retry interrupted for: " + context, ie);
                }
                delay = (long) (delay * backoffMultiplier);
            }
        }

        throw new RetryableException("All " + maxAttempts + " attempts failed for: " + context
                + (lastResponse != null ? " (last status: " + lastResponse.statusCode() + ")" : ""));
    }

    /**
     * Convenience: retries until status is in the 2xx range.
     */
    public Response executeWithRetry(Callable<Response> action, String context) {
        return executeWithRetry(action, r -> r.statusCode() >= 200 && r.statusCode() < 300, context);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private static int parseOrDefault(String key, int defaultValue) {
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

    private static long parseOrDefault(String key, long defaultValue) {
        String val = ConfigManager.getOptional(key);
        if (val != null) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static double parseOrDefaultDouble(String key, double defaultValue) {
        String val = ConfigManager.getOptional(key);
        if (val != null) {
            try {
                return Double.parseDouble(val.trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return defaultValue;
    }

    /**
     * Exception thrown when all retry attempts are exhausted.
     */
    public static class RetryableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RetryableException(String message) {
            super(message);
        }

        public RetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
