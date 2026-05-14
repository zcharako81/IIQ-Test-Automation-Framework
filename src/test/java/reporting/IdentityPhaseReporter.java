package reporting;

import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.xml.XmlSuite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom TestNG {@link IReporter} that generates an enhanced
 * {@code emailable-report.html} for identity lifecycle tests.
 * <p>
 * Parses the structured {@link Reporter#log(String)} output from
 * {@link tests.identity.IdentityTest} and renders per-identity phase tables
 * with color-coded rows, duration bars, and expandable detail sections
 * showing verified attributes, roles, and account attributes.
 */
public class IdentityPhaseReporter implements IReporter {

    // ── Log line patterns ────────────────────────────────────────────────
    private static final Pattern HEADER_PATTERN =
            Pattern.compile("=== Starting identity lifecycle \\(suffix: (.+)\\) ===");
    private static final Pattern IDENTITY_START_PATTERN =
            Pattern.compile("=== Identity: (\\S+) \\((\\d+) phases\\) ===");
    private static final Pattern IDENTITY_COMPLETE_PATTERN =
            Pattern.compile("=== Identity: (\\S+) complete ===");
    private static final Pattern PHASE_DURATION =
            Pattern.compile("  Phase: (.+) -> (\\d+)ms");
    private static final Pattern PHASE_START =
            Pattern.compile("  Phase: (.+)");

    // Phase description line (4 spaces, "[desc] <text>")
    private static final Pattern PHASE_DESC =
            Pattern.compile("    \\[desc\\] (.+)");

    // Detail summary lines (2-space indent)
    private static final Pattern DETAIL_VERIFY_IDENTITY =
            Pattern.compile("  \\[verifyIdentity\\] Attributes checked: (\\d+)");
    private static final Pattern DETAIL_VERIFY_ROLES =
            Pattern.compile("  \\[verifyRoles\\] Expected: \\[(.*)\\] matched (\\d+)/(\\d+)");
    private static final Pattern DETAIL_VERIFY_ACCOUNTS_EXISTS =
            Pattern.compile("  \\[verifyAccounts\\] App: (.+) \\((\\d+) attrs\\)");
    private static final Pattern DETAIL_VERIFY_ACCOUNTS_MISSING =
            Pattern.compile("  \\[verifyAccounts\\] App: (.+) \\(should not exist\\)");

    // Sub-detail lines (4-space indent)
    private static final Pattern SUB_ATTR =
            Pattern.compile("    \\[attr\\] (.+) → (.+)");
    private static final Pattern SUB_ROLE =
            Pattern.compile("    \\[role\\] (.+) (.+)");
    private static final Pattern SUB_ACCT =
            Pattern.compile("    \\[acct:(.+)\\] (.+) → (.+)");

    private static final Pattern SUMMARY_PATTERN =
            Pattern.compile("=== All phases completed in (\\d+)ms ===");

    // ── Data models ──────────────────────────────────────────────────────

    /**
     * A single granular check logged by the test (one attribute, one role, or
     * one account attribute). These are grouped under a {@link DetailItem}.
     */
    static class SubDetail {
        /** Category: "attr", "role", or the account type name (e.g. "ldap"). */
        String type;
        /** Label shown in the report (e.g. "userName", "ALL_ACTIVE_USERS", "uid"). */
        String label;
        /** Value shown in the report (e.g. "john.doe", "✓", "john.doe"). */
        String value;
        /** Whether this check passed. */
        boolean passed;
    }

    /**
     * A detail summary line ({@code [verifyIdentity]}, {@code [verifyRoles]},
     * or {@code [verifyAccounts]}) together with its granular sub-detail items.
     */
    static class DetailItem {
        /** "verifyIdentity", "verifyRoles", or "verifyAccounts". */
        String category;
        /** Short summary text (e.g. "Attributes checked: 8"). */
        String summary;
        /** Individual granular checks. */
        List<SubDetail> subDetails = new ArrayList<>();
    }

    static class PhaseEntry {
        String name;
        long durationMs;
        boolean passed;
        /** Optional human-readable description (from identity.json {@code descriptions} map). */
        String description;
        /** Detail summary lines attached to this phase. */
        List<DetailItem> details = new ArrayList<>();
    }

    static class IdentityReport {
        String key;
        List<PhaseEntry> phases = new ArrayList<>();
        long totalDuration;
        boolean passed;
        int phaseCount;
        /** Full assertion-error message from SoftAssert.assertAll(), if any. */
        String failureMessage;
    }

    // ── IReporter entry point ────────────────────────────────────────────

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites,
                               String outputDirectory) {
        // ── Collect test results ─────────────────────────────────────────
        List<ITestResult> allResults = new ArrayList<>();
        Map<String, Boolean> testPassMap = new LinkedHashMap<>();

        for (ISuite suite : suites) {
            for (ISuiteResult result : suite.getResults().values()) {
                ITestContext ctx = result.getTestContext();
                for (ITestResult tr : ctx.getPassedTests().getAllResults()) {
                    allResults.add(tr);
                    testPassMap.put(tr.getName(), true);
                }
                for (ITestResult tr : ctx.getFailedTests().getAllResults()) {
                    allResults.add(tr);
                    testPassMap.put(tr.getName(), false);
                }
            }
        }

        // ── Collect failure messages from failed test results ────────────
        // Each failure line has the form:
        //   \tMismatch for <attr> on: <identityKey> expected [<expected>] but found [<actual>]
        // We build a map: identityKey → (combined failure message)
        Map<String, String> failureMessagesByIdentity = new LinkedHashMap<>();
        for (ITestResult tr : allResults) {
            if (!testPassMap.getOrDefault(tr.getName(), true) && tr.getThrowable() != null) {
                String msg = tr.getThrowable().getMessage();
                if (msg != null) {
                    // Extract each identity key referenced in the failure lines
                    for (String line : msg.split("\n")) {
                        line = line.trim();
                        // "Mismatch for <attr> on: <key> expected ..."
                        int onIdx = line.indexOf(" on: ");
                        if (onIdx >= 0) {
                            int keyStart = onIdx + 5;
                            int keyEnd = line.indexOf(" expected", keyStart);
                            if (keyEnd < 0) keyEnd = line.length();
                            String identityKey = line.substring(keyStart, keyEnd).trim();
                            // Collect unique identity keys with their full message
                            failureMessagesByIdentity.putIfAbsent(identityKey, msg);
                        }
                    }
                }
            }
        }

        if (allResults.isEmpty()) {
            return;
        }

        // ── Parse Reporter output into identity reports ──────────────────
        String suffix = "";
        long totalRunDuration = 0;
        List<IdentityReport> identityReports = new ArrayList<>();
        IdentityReport currentIdentity = null;

        // Collect all Reporter output lines across all test results
        List<String> allOutput = new ArrayList<>();
        for (ITestResult tr : allResults) {
            List<String> output = Reporter.getOutput(tr);
            if (output != null) {
                allOutput.addAll(output);
            }
        }

        // Current detail item being populated by sub-detail lines
        DetailItem currentDetail = null;

        for (String line : allOutput) {
            // Run header
            Matcher headerMatcher = HEADER_PATTERN.matcher(line);
            if (headerMatcher.matches()) {
                suffix = headerMatcher.group(1);
                continue;
            }

            // Identity start
            Matcher idStartMatcher = IDENTITY_START_PATTERN.matcher(line);
            if (idStartMatcher.matches()) {
                currentIdentity = new IdentityReport();
                currentIdentity.key = idStartMatcher.group(1);
                currentIdentity.phaseCount = Integer.parseInt(idStartMatcher.group(2));
                currentIdentity.passed = true;
                identityReports.add(currentIdentity);
                currentDetail = null;
                continue;
            }

            // Identity complete
            if (IDENTITY_COMPLETE_PATTERN.matcher(line).matches()) {
                if (currentIdentity != null) {
                    currentIdentity.totalDuration = currentIdentity.phases.stream()
                            .mapToLong(p -> p.durationMs)
                            .sum();
                }
                currentIdentity = null;
                currentDetail = null;
                continue;
            }

            // Phase duration update ("Phase: verifyCreate -> 312ms")
            Matcher durMatcher = PHASE_DURATION.matcher(line);
            if (durMatcher.matches() && currentIdentity != null && !currentIdentity.phases.isEmpty()) {
                String name = durMatcher.group(1);
                long duration = Long.parseLong(durMatcher.group(2));
                PhaseEntry last = currentIdentity.phases.get(currentIdentity.phases.size() - 1);
                if (last.name.equals(name)) {
                    last.durationMs = duration;
                }
                continue;
            }

            // Phase start ("Phase: verifyCreate" — no duration yet)
            Matcher startMatcher = PHASE_START.matcher(line);
            if (startMatcher.matches() && currentIdentity != null) {
                PhaseEntry entry = new PhaseEntry();
                entry.name = startMatcher.group(1);
                entry.passed = true;
                currentIdentity.phases.add(entry);
                currentDetail = null;
                continue;
            }

            // Phase description ("    [desc] Update title and end-date")
            if (currentIdentity != null && !currentIdentity.phases.isEmpty()) {
                Matcher descMatcher = PHASE_DESC.matcher(line);
                if (descMatcher.matches()) {
                    PhaseEntry last = currentIdentity.phases.get(currentIdentity.phases.size() - 1);
                    last.description = descMatcher.group(1);
                    continue;
                }
            }

            if (currentIdentity == null || currentIdentity.phases.isEmpty()) {
                // Run summary (may appear outside identity sections)
                Matcher summaryMatcher = SUMMARY_PATTERN.matcher(line);
                if (summaryMatcher.matches()) {
                    totalRunDuration = Long.parseLong(summaryMatcher.group(1));
                }
                continue;
            }

            PhaseEntry lastPhase = currentIdentity.phases.get(currentIdentity.phases.size() - 1);

            // ── Detail summary lines ─────────────────────────────────────

            Matcher viMatcher = DETAIL_VERIFY_IDENTITY.matcher(line);
            if (viMatcher.matches()) {
                currentDetail = new DetailItem();
                currentDetail.category = "verifyIdentity";
                currentDetail.summary = "Attributes checked: " + viMatcher.group(1);
                lastPhase.details.add(currentDetail);
                continue;
            }

            Matcher vrMatcher = DETAIL_VERIFY_ROLES.matcher(line);
            if (vrMatcher.matches()) {
                currentDetail = new DetailItem();
                currentDetail.category = "verifyRoles";
                currentDetail.summary = "Roles: [" + vrMatcher.group(1)
                        + "] matched " + vrMatcher.group(2) + "/" + vrMatcher.group(3);
                lastPhase.details.add(currentDetail);
                continue;
            }

            Matcher vaExistMatcher = DETAIL_VERIFY_ACCOUNTS_EXISTS.matcher(line);
            if (vaExistMatcher.matches()) {
                currentDetail = new DetailItem();
                currentDetail.category = "verifyAccounts";
                currentDetail.summary = "App: " + vaExistMatcher.group(1)
                        + " (" + vaExistMatcher.group(2) + " attrs)";
                lastPhase.details.add(currentDetail);
                continue;
            }

            Matcher vaMissMatcher = DETAIL_VERIFY_ACCOUNTS_MISSING.matcher(line);
            if (vaMissMatcher.matches()) {
                currentDetail = new DetailItem();
                currentDetail.category = "verifyAccounts";
                currentDetail.summary = "App: " + vaMissMatcher.group(1) + " (should not exist)";
                lastPhase.details.add(currentDetail);
                continue;
            }

            // ── Sub-detail lines (attach to currentDetail) ───────────────
            if (currentDetail != null) {
                SubDetail sd = null;

                Matcher attrMatcher = SUB_ATTR.matcher(line);
                if (attrMatcher.matches()) {
                    sd = new SubDetail();
                    sd.type = "attr";
                    sd.label = attrMatcher.group(1);
                    sd.value = attrMatcher.group(2);
                    sd.passed = true;
                }

                if (sd == null) {
                    Matcher roleMatcher = SUB_ROLE.matcher(line);
                    if (roleMatcher.matches()) {
                        sd = new SubDetail();
                        sd.type = "role";
                        sd.label = roleMatcher.group(1);
                        sd.value = roleMatcher.group(2).trim();
                        sd.passed = "\u2713".equals(sd.value) || "\u2713".equals(sd.value.trim());
                    }
                }

                if (sd == null) {
                    Matcher acctMatcher = SUB_ACCT.matcher(line);
                    if (acctMatcher.matches()) {
                        sd = new SubDetail();
                        sd.type = "acct:" + acctMatcher.group(1);
                        sd.label = acctMatcher.group(2);
                        sd.value = acctMatcher.group(3);
                        sd.passed = true;
                    }
                }

                if (sd != null) {
                    currentDetail.subDetails.add(sd);
                }
            }

            // Run summary (may appear outside identity sections)
            Matcher summaryMatcher = SUMMARY_PATTERN.matcher(line);
            if (summaryMatcher.matches()) {
                totalRunDuration = Long.parseLong(summaryMatcher.group(1));
            }
        }

        // ── Secondary identity-key extraction from raw error message ─────
        // If the " on: " pattern didn't match any lines (e.g. the assertion
        // uses " for: " or another format), scan the raw error for identity keys.
        String rawErrorMessage = null;
        for (ITestResult tr : allResults) {
            if (!testPassMap.getOrDefault(tr.getName(), true) && tr.getThrowable() != null) {
                rawErrorMessage = tr.getThrowable().getMessage();
                break;
            }
        }
        if (rawErrorMessage != null && failureMessagesByIdentity.isEmpty()) {
            for (IdentityReport idr : identityReports) {
                if (rawErrorMessage.contains(idr.key)) {
                    failureMessagesByIdentity.put(idr.key, rawErrorMessage);
                }
            }
        }

        // Determine per-identity pass/fail from failure messages
        // Only identities whose keys appear in the error message are marked failed.
        boolean hasParsedFailures = !failureMessagesByIdentity.isEmpty();

        for (IdentityReport idr : identityReports) {
            if (hasParsedFailures) {
                // Each identity independently — only fail those referenced in error lines
                idr.passed = !failureMessagesByIdentity.containsKey(idr.key);
            } else {
                // Fallback: no identity keys found anywhere in the error text;
                // mark all identities failed if the test method itself failed.
                idr.passed = !testPassMap.values().stream().anyMatch(v -> !v);
            }
            if (!idr.passed) {
                idr.failureMessage = failureMessagesByIdentity.get(idr.key);
                if (idr.failureMessage == null) {
                    idr.failureMessage = "Test failed (see header for details).";
                }
                markFailedPhases(idr, idr.failureMessage);
            }
        }

        int totalPassed = (int) testPassMap.values().stream().filter(v -> v).count();
        int totalFailed = (int) testPassMap.values().stream().filter(v -> !v).count();

        // ── Fallback total duration ───────────────────────────────────────
        // When the test fails, softAssert.assertAll() throws before the
        // "=== All phases completed in Nms ===" summary line is logged, so
        // totalRunDuration stays 0.  Compute it from identity totals instead.
        if (totalRunDuration == 0 && !identityReports.isEmpty()) {
            totalRunDuration = identityReports.stream()
                    .mapToLong(idr -> idr.totalDuration)
                    .sum();
        }

        // ── Generate HTML ────────────────────────────────────────────────
        String html = buildHtml(suffix, totalRunDuration, totalPassed, totalFailed,
                identityReports);

        // ── Write emailable-report.html ───────────────────────────────────
        File outDir = new File(outputDirectory != null ? outputDirectory : "test-output");
        outDir.mkdirs();
        File reportFile = new File(outDir, "emailable-report.html");
        try (PrintWriter pw = new PrintWriter(new FileWriter(reportFile))) {
            pw.print(html);
        } catch (IOException e) {
            System.err.println("[IdentityPhaseReporter] Failed to write report: " + e.getMessage());
        }
    }

    // ── HTML builder ────────────────────────────────────────────────────

    private String buildHtml(String suffix, long totalRunDuration,
                             int totalPassed, int totalFailed,
                             List<IdentityReport> identityReports) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Identity Lifecycle Report</title>\n");
        sb.append("<style>\n");
        sb.append(cssStyles());
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<div class=\"container\">\n");

        // ── Header ────────────────────────────────────────────────────────
        String overallStatus = totalFailed == 0 ? "PASSED" : "FAILED";
        String statusIcon = totalFailed == 0 ? "\u2705" : "\u274C";
        sb.append("<header class=\"").append(overallStatus.toLowerCase()).append("\">\n");
        sb.append("  <div class=\"header-content\">\n");
        sb.append("    <h1>").append(statusIcon).append(" Identity Lifecycle Report</h1>\n");
        sb.append("    <div class=\"header-meta\">\n");
        sb.append("      <span class=\"meta-tag\">").append(overallStatus).append("</span>\n");
        sb.append("      <span class=\"meta-sep\">|</span>\n");
        sb.append("      <span>").append(identityReports.size()).append(" identities</span>\n");
        sb.append("      <span class=\"meta-sep\">|</span>\n");
        sb.append("      <span>Total: ").append(formatDuration(totalRunDuration)).append("</span>\n");
        sb.append("      <span class=\"meta-sep\">|</span>\n");
        sb.append("      <span>").append(timestamp).append("</span>\n");
        if (!suffix.isEmpty()) {
            sb.append("      <span class=\"meta-sep\">|</span>\n");
            sb.append("      <span>Suffix: ").append(escapeHtml(suffix)).append("</span>\n");
        }
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</header>\n");

        // ── Summary cards ─────────────────────────────────────────────────
        sb.append("<div class=\"summary-row\">\n");
        sb.append(summaryCard("total", "Identities", String.valueOf(identityReports.size()),
                "\uD83D\uDC64"));
        sb.append(summaryCard("passed", "Passed", String.valueOf(totalPassed), "\u2705"));
        sb.append(summaryCard("failed", "Failed", String.valueOf(totalFailed), "\u274C"));
        sb.append(summaryCard("duration", "Duration", formatDuration(totalRunDuration), "\u23F1\uFE0F"));
        sb.append("</div>\n");

        // ── Per-identity sections ─────────────────────────────────────────
        for (IdentityReport idr : identityReports) {
            sb.append(identitySection(idr));
        }

        // ── Footer ────────────────────────────────────────────────────────
        sb.append("<footer>\n");
        sb.append("  <p>Generated by IdentityPhaseReporter &mdash; SailPoint IIQ Test Automation Framework</p>\n");
        sb.append("</footer>\n");

        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    // ── CSS ──────────────────────────────────────────────────────────────

    private String cssStyles() {
        return """
                /* ── Reset & Base ─────────────────────────────────────── */
                *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
                               'Helvetica Neue', Arial, sans-serif;
                  background: #f0f2f5;
                  color: #1a1a2e;
                  line-height: 1.6;
                  padding: 24px;
                }
                .container { max-width: 1100px; margin: 0 auto; }

                /* ── Header ─────────────────────────────────────────────── */
                header {
                  border-radius: 12px;
                  padding: 32px 40px;
                  margin-bottom: 24px;
                  color: #fff;
                  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
                }
                header.passed {
                  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
                }
                header.failed {
                  background: linear-gradient(135deg, #cb2d3e 0%, #ef473a 100%);
                }
                .header-content h1 {
                  font-size: 28px;
                  font-weight: 700;
                  margin-bottom: 8px;
                  letter-spacing: -0.5px;
                }
                .header-meta {
                  font-size: 14px;
                  opacity: 0.92;
                  display: flex;
                  flex-wrap: wrap;
                  gap: 6px;
                  align-items: center;
                }
                .meta-tag {
                  display: inline-block;
                  background: rgba(255,255,255,0.25);
                  padding: 2px 10px;
                  border-radius: 4px;
                  font-weight: 600;
                  font-size: 13px;
                  text-transform: uppercase;
                  letter-spacing: 0.5px;
                }
                .meta-sep { opacity: 0.5; }

                /* ── Summary cards row ──────────────────────────────────── */
                .summary-row {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                  gap: 16px;
                  margin-bottom: 28px;
                }
                .summary-card {
                  background: #fff;
                  border-radius: 10px;
                  padding: 20px;
                  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
                  text-align: center;
                  border-top: 4px solid #e0e0e0;
                }
                .summary-card .card-icon { font-size: 28px; margin-bottom: 4px; }
                .summary-card .card-label {
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.8px;
                  color: #888;
                  font-weight: 600;
                }
                .summary-card .card-value {
                  font-size: 32px;
                  font-weight: 700;
                  color: #1a1a2e;
                }
                .summary-card.card-passed { border-top-color: #11998e; }
                .summary-card.card-failed { border-top-color: #cb2d3e; }
                .summary-card.card-duration { border-top-color: #4a90d9; }
                .summary-card.card-total { border-top-color: #6c5ce7; }

                /* ── Identity section ───────────────────────────────────── */
                .identity-card {
                  background: #fff;
                  border-radius: 12px;
                  margin-bottom: 20px;
                  box-shadow: 0 2px 12px rgba(0,0,0,0.07);
                  overflow: hidden;
                }
                .identity-header {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 18px 28px;
                  border-bottom: 1px solid #eee;
                  flex-wrap: wrap;
                  gap: 8px;
                }
                .identity-header .id-name {
                  font-size: 18px;
                  font-weight: 600;
                  display: flex;
                  align-items: center;
                  gap: 8px;
                }
                .identity-header .id-badge {
                  display: inline-block;
                  background: #e8f5e9;
                  color: #2e7d32;
                  font-size: 11px;
                  font-weight: 700;
                  padding: 2px 8px;
                  border-radius: 4px;
                  text-transform: uppercase;
                  letter-spacing: 0.3px;
                }
                .identity-header .id-badge.failed {
                  background: #ffebee;
                  color: #c62828;
                }
                .identity-header .id-summary {
                  font-size: 13px;
                  color: #666;
                  display: flex;
                  gap: 16px;
                  align-items: center;
                }

                /* ── Identity failure section ────────────────────────────── */
                .identity-error {
                  margin: 8px 28px 12px 28px;
                  background: #fff5f5;
                  border: 1px solid #fecaca;
                  border-left: 4px solid #dc2626;
                  border-radius: 8px;
                  padding: 12px 16px;
                }
                .identity-error .error-header {
                  font-weight: 700;
                  font-size: 14px;
                  color: #dc2626;
                  margin-bottom: 6px;
                }
                .identity-error .error-detail {
                  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
                  font-size: 12px;
                  color: #991b1b;
                  background: #fef2f2;
                  padding: 8px 12px;
                  border-radius: 4px;
                  white-space: pre-wrap;
                  word-break: break-word;
                  line-height: 1.5;
                  overflow-x: auto;
                }
                .identity-error .error-more {
                  margin-top: 4px;
                  font-size: 12px;
                }
                .identity-error .error-more summary {
                  cursor: pointer;
                  color: #dc2626;
                  font-weight: 500;
                  padding: 2px 0;
                }

                /* ── Description column ─────────────────────────────── */
                .phase-desc-cell {
                  font-size: 13px;
                  color: #666;
                  line-height: 1.4;
                  vertical-align: middle;
                }

                /* ── Phase table ────────────────────────────────────────── */
                .phase-table {
                  width: 100%;
                  border-collapse: collapse;
                  font-size: 14px;
                }
                .phase-table th {
                  text-align: left;
                  padding: 10px 28px;
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.8px;
                  color: #999;
                  font-weight: 600;
                  background: #fafafa;
                  border-bottom: 2px solid #eee;
                }
                .phase-table td {
                  padding: 12px 28px;
                  border-bottom: 1px solid #f0f0f0;
                  vertical-align: middle;
                }
                .phase-table tr:last-child td { border-bottom: none; }

                /* Phase name column */
                .phase-name {
                  font-weight: 500;
                  display: flex;
                  align-items: center;
                  gap: 8px;
                }
                .phase-icon {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  width: 24px;
                  height: 24px;
                  border-radius: 50%;
                  font-size: 12px;
                  flex-shrink: 0;
                }
                .phase-icon.task    { background: #e3f2fd; color: #1565c0; }
                .phase-icon.create  { background: #e8f5e9; color: #2e7d32; }
                .phase-icon.verify  { background: #e8f5e9; color: #2e7d32; }
                .phase-icon.modify  { background: #fff3e0; color: #e65100; }
                .phase-icon.delete  { background: #ffebee; color: #c62828; }

                /* Duration column */
                .phase-duration {
                  font-weight: 600;
                  white-space: nowrap;
                  min-width: 70px;
                }

                /* Duration bar */
                .dur-bar-wrapper {
                  display: flex;
                  align-items: center;
                  justify-content: flex-end;
                  gap: 8px;
                }
                .dur-bar {
                  height: 8px;
                  border-radius: 4px;
                  min-width: 40px;
                  flex: 1;
                  background: #e0e0e0;
                  overflow: hidden;
                }
                .dur-bar-fill {
                  height: 100%;
                  border-radius: 4px;
                  transition: width 0.3s;
                  background: linear-gradient(90deg, #42a5f5, #1565c0);
                }

                /* Status indicator */
                .phase-status { font-size: 18px; text-align: center; }

                /* ── Detail rows (expandable sub-sections) ──────────────── */
                .detail-row td {
                  padding: 0;
                  border-bottom: none;
                  background: #fafafa;
                }
                .detail-row .detail-inner {
                  padding: 6px 28px 10px 74px;
                }

                /* Collapsible <details> inside detail rows */
                .detail-inner details {
                  margin: 4px 0;
                  font-size: 13px;
                }
                .detail-inner details summary {
                  cursor: pointer;
                  color: #555;
                  font-weight: 500;
                  padding: 2px 0;
                  user-select: none;
                }
                .detail-inner details summary:hover {
                  color: #1a1a2e;
                }
                .detail-inner details[open] summary {
                  margin-bottom: 4px;
                }

                /* Attribute list */
                .attr-grid {
                  display: grid;
                  grid-template-columns: auto 1fr;
                  gap: 2px 12px;
                  padding: 4px 0 4px 16px;
                  font-size: 12px;
                  color: #444;
                }
                .attr-grid .attr-key {
                  font-weight: 600;
                  color: #333;
                  text-align: right;
                  white-space: nowrap;
                }
                .attr-grid .attr-val {
                  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
                  color: #555;
                  word-break: break-all;
                }

                /* Role list */
                .role-list {
                  list-style: none;
                  padding: 4px 0 4px 16px;
                  font-size: 12px;
                }
                .role-list li {
                  padding: 1px 0;
                  display: flex;
                  align-items: center;
                  gap: 6px;
                }
                .role-list .role-ok { color: #2e7d32; }
                .role-list .role-fail { color: #c62828; }

                /* Account sub-table */
                .acct-table {
                  border-collapse: collapse;
                  font-size: 12px;
                  margin: 4px 0 4px 16px;
                }
                .acct-table td {
                  padding: 2px 10px 2px 0;
                  border: none !important;
                  background: transparent !important;
                }
                .acct-table .acct-key {
                  font-weight: 600;
                  color: #333;
                  text-align: right;
                  padding-right: 10px;
                  white-space: nowrap;
                }
                .acct-table .acct-val {
                  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
                  color: #555;
                  word-break: break-all;
                }

                /* Section label within details */
                .section-label {
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.5px;
                  color: #999;
                  font-weight: 600;
                  margin: 6px 0 2px 0;
                  padding-left: 16px;
                }

                /* ── Footer ─────────────────────────────────────────────── */
                footer {
                  text-align: center;
                  padding: 20px;
                  color: #999;
                  font-size: 12px;
                }

                /* ── Dark mode ──────────────────────────────────────────── */
                @media (prefers-color-scheme: dark) {
                  body { background: #1a1a2e; color: #e0e0e0; }
                  .summary-card,
                  .identity-card { background: #16213e; }
                  .identity-header { border-bottom-color: #2a2a4a; }
                  .phase-table th {
                    background: #1a1a3e;
                    color: #aaa;
                    border-bottom-color: #2a2a4a;
                  }
                  .phase-table td { border-bottom-color: #2a2a4a; }
                  .phase-table tr:last-child td { border-bottom: none; }
                  .detail-row td { background: #1a1a3e; }
                  .detail-inner details summary { color: #aaa; }
                  .detail-inner details summary:hover { color: #e0e0e0; }
                  .attr-grid .attr-key { color: #ccc; }
                  .attr-grid .attr-val { color: #aaa; }
                  .acct-table .acct-key { color: #ccc; }
                  .acct-table .acct-val { color: #aaa; }
                  .summary-card .card-value { color: #e0e0e0; }
                  .identity-header .id-summary { color: #aaa; }
                  .identity-header { border-bottom-color: #2a2a4a; }
                  .section-label { color: #777; }
                  .identity-error {
                    background: #2a1515;
                    border-color: #4a2020;
                    border-left-color: #dc2626;
                  }
                  .identity-error .error-header { color: #f87171; }
                  .identity-error .error-detail {
                    background: #1f1010;
                    color: #fca5a5;
                  }
                  .identity-error .error-more summary { color: #f87171; }
                  .phase-desc-cell { color: #aaa; }
                }

                /* ── Print ──────────────────────────────────────────────── */
                @media print {
                  body { background: #fff; padding: 0; }
                  header { box-shadow: none; break-inside: avoid; }
                  .identity-card { break-inside: avoid; box-shadow: none; border: 1px solid #ddd; }
                }
                """;
    }

    // ── Summary card HTML ───────────────────────────────────────────────

    private String summaryCard(String type, String label, String value, String icon) {
        return "<div class=\"summary-card card-" + type + "\">\n"
                + "  <div class=\"card-icon\">" + icon + "</div>\n"
                + "  <div class=\"card-label\">" + label + "</div>\n"
                + "  <div class=\"card-value\">" + value + "</div>\n"
                + "</div>\n";
    }

    // ── Identity section HTML ──────────────────────────────────────────

    private String identitySection(IdentityReport idr) {
        StringBuilder sb = new StringBuilder();
        String badgeClass = idr.passed ? "" : " failed";
        String badgeText = idr.passed ? "\u2705 Passed" : "\u274C Failed";
        long maxDuration = idr.phases.stream()
                .mapToLong(p -> p.durationMs)
                .max().orElse(1);
        if (maxDuration == 0) maxDuration = 1;

        sb.append("<div class=\"identity-card\">\n");
        sb.append("  <div class=\"identity-header\">\n");
        sb.append("    <div class=\"id-name\">\n");
        sb.append("      <span>\uD83D\uDC64</span>\n");
        sb.append("      <span>").append(escapeHtml(idr.key)).append("</span>\n");
        sb.append("      <span class=\"id-badge").append(badgeClass).append("\">")
                .append(badgeText).append("</span>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"id-summary\">\n");
        sb.append("      <span>").append(idr.phases.size()).append(" phases</span>\n");
        sb.append("      <span>").append(formatDuration(idr.totalDuration)).append(" total</span>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // ── Failure error section (if any) ────────────────────────────────
        if (!idr.passed && idr.failureMessage != null) {
            sb.append("  <div class=\"identity-error\">\n");
            sb.append("    <div class=\"error-header\">\u274C Test Assertions Failed</div>\n");
            // Show first N chars as a preview, rest expandable
            String msg = idr.failureMessage.trim();
            int previewLen = Math.min(msg.length(), 300);
            String preview = escapeHtml(msg.substring(0, previewLen));
            String rest = msg.length() > previewLen
                    ? escapeHtml(msg.substring(previewLen))
                    : "";
            sb.append("    <pre class=\"error-detail\">").append(preview).append("</pre>\n");
            if (!rest.isEmpty()) {
                sb.append("    <details class=\"error-more\">\n");
                sb.append("      <summary>Show full failure details</summary>\n");
                sb.append("      <pre class=\"error-detail\">").append(rest).append("</pre>\n");
                sb.append("    </details>\n");
            }
            sb.append("  </div>\n");
        }

        // Phase table
        sb.append("  <table class=\"phase-table\">\n");
        sb.append("    <thead>\n");
        sb.append("      <tr>\n");
        sb.append("        <th style=\"width:22%\">Phase</th>\n");
        sb.append("        <th style=\"width:30%\">Description</th>\n");
        sb.append("        <th style=\"width:33%; text-align:center;\">Duration</th>\n");
        sb.append("        <th style=\"width:10%; text-align:center;\">Status</th>\n");
        sb.append("      </tr>\n");
        sb.append("    </thead>\n");
        sb.append("    <tbody>\n");

        for (PhaseEntry phase : idr.phases) {
            String phaseType = classifyPhase(phase.name);
            String iconChar = phaseIcon(phaseType);
            double pct = (double) phase.durationMs / maxDuration * 100;

            sb.append("      <tr>\n");
            sb.append("        <td>\n");
            sb.append("          <div class=\"phase-name\">\n");
            sb.append("            <span class=\"phase-icon ").append(phaseType).append("\">")
                    .append(iconChar).append("</span>\n");
            sb.append("            <span>").append(escapeHtml(phase.name)).append("</span>\n");
            sb.append("          </div>\n");
            sb.append("        </td>\n");
            sb.append("        <td class=\"phase-desc-cell\">\n");
            if (phase.description != null) {
                sb.append("          <span>").append(escapeHtml(phase.description)).append("</span>\n");
            }
            sb.append("        </td>\n");
            sb.append("        <td>\n");
            sb.append("          <div class=\"dur-bar-wrapper\">\n");
            sb.append("            <div class=\"dur-bar\">\n");
            sb.append("              <div class=\"dur-bar-fill\"")
                    .append(" style=\"width:").append(String.format("%.1f", pct)).append("%\"></div>\n");
            sb.append("            </div>\n");
            sb.append("            <span class=\"phase-duration\">")
                    .append(formatDuration(phase.durationMs)).append("</span>\n");
            sb.append("          </div>\n");
            sb.append("        </td>\n");
            sb.append("        <td class=\"phase-status\">").append(phase.passed ? "\u2705" : "\u274C").append("</td>\n");
            sb.append("      </tr>\n");

            // Detail sub-rows (if any)
            if (!phase.details.isEmpty()) {
                sb.append("      <tr class=\"detail-row\">\n");
                sb.append("        <td colspan=\"4\">\n");
                sb.append("          <div class=\"detail-inner\">\n");
                for (DetailItem detail : phase.details) {
                    sb.append(renderDetailItem(detail));
                }
                sb.append("          </div>\n");
                sb.append("        </td>\n");
                sb.append("      </tr>\n");
            }
        }

        sb.append("    </tbody>\n");
        sb.append("  </table>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    // ── Detail item renderer ────────────────────────────────────────────

    /**
     * Renders a single detail item as a collapsible HTML
     * {@code <details>} section.
     * <p>
     * Depending on the category, the expanded content shows:
     * <ul>
     *   <li>{@code verifyIdentity} — attribute grid (key → value)</li>
     *   <li>{@code verifyRoles} — role list with checkmarks</li>
     *   <li>{@code verifyAccounts} — per-account attribute tables</li>
     * </ul>
     */
    private String renderDetailItem(DetailItem item) {
        StringBuilder sb = new StringBuilder();

        // Determine icon and colour for the summary line
        String summaryIcon;
        switch (item.category) {
            case "verifyIdentity":
                summaryIcon = "\uD83D\uDCCB";  // 📋
                break;
            case "verifyRoles":
                summaryIcon = "\uD83C\uDFC6";  // 🏆
                break;
            case "verifyAccounts":
                summaryIcon = "\uD83D\uDD17";  // 🔗
                break;
            default:
                summaryIcon = "\u2139\uFE0F";  // ℹ️
        }

        sb.append("            <details>\n");
        sb.append("              <summary>")
                .append(summaryIcon).append(" ").append(escapeHtml(item.summary))
                .append("</summary>\n");

        if (!item.subDetails.isEmpty()) {
            // Group sub-details by type
            List<SubDetail> attrs = new ArrayList<>();
            List<SubDetail> roles = new ArrayList<>();
            List<Map<String, List<SubDetail>>> acctGroups = new ArrayList<>();
            Map<String, List<SubDetail>> acctMap = new LinkedHashMap<>();

            for (SubDetail sd : item.subDetails) {
                if ("attr".equals(sd.type)) {
                    attrs.add(sd);
                } else if ("role".equals(sd.type)) {
                    roles.add(sd);
                } else if (sd.type.startsWith("acct:")) {
                    String acctType = sd.type.substring(5); // strip "acct:" prefix
                    acctMap.computeIfAbsent(acctType, k -> new ArrayList<>()).add(sd);
                }
            }

            // Render attributes (verifyIdentity)
            if (!attrs.isEmpty()) {
                sb.append("                <div class=\"section-label\">Attributes</div>\n");
                sb.append("                <div class=\"attr-grid\">\n");
                for (SubDetail sd : attrs) {
                    sb.append("                  <div class=\"attr-key\">")
                            .append(escapeHtml(sd.label)).append("</div>\n");
                    sb.append("                  <div class=\"attr-val\">")
                            .append(escapeHtml(sd.value)).append("</div>\n");
                }
                sb.append("                </div>\n");
            }

            // Render roles (verifyRoles)
            if (!roles.isEmpty()) {
                sb.append("                <div class=\"section-label\">Roles</div>\n");
                sb.append("                <ul class=\"role-list\">\n");
                for (SubDetail sd : roles) {
                    boolean ok = "\u2713".equals(sd.value) || "✓".equals(sd.value);
                    sb.append("                  <li>")
                            .append("<span class=\"").append(ok ? "role-ok" : "role-fail").append("\">")
                            .append(ok ? "\u2705" : "\u274C")
                            .append("</span>")
                            .append(escapeHtml(sd.label))
                            .append("</li>\n");
                }
                sb.append("                </ul>\n");
            }

            // Render account attributes (verifyAccounts)
            if (!acctMap.isEmpty()) {
                for (Map.Entry<String, List<SubDetail>> acctEntry : acctMap.entrySet()) {
                    String acctType = acctEntry.getKey();
                    List<SubDetail> acctAttrs = acctEntry.getValue();
                    sb.append("                <div class=\"section-label\">Account: ")
                            .append(escapeHtml(acctType)).append("</div>\n");
                    sb.append("                <table class=\"acct-table\">\n");
                    for (SubDetail sd : acctAttrs) {
                        sb.append("                  <tr>");
                        sb.append("<td class=\"acct-key\">").append(escapeHtml(sd.label)).append("</td>");
                        sb.append("<td class=\"acct-val\">").append(escapeHtml(sd.value)).append("</td>");
                        sb.append("</tr>\n");
                    }
                    sb.append("                </table>\n");
                }
            }
        }

        sb.append("            </details>\n");
        return sb.toString();
    }

    // ── Failure-to-phase matching ───────────────────────────────────────

    /**
     * Examines the failure message and marks relevant phases as failed.
     * <p>
     * Heuristic: each line starting with "Mismatch for &lt;attr&gt; on: …" is classified:
     * <ul>
     *   <li>If &lt;attr&gt; contains "role" → mark verifyRoles phases</li>
     *   <li>If &lt;attr&gt; contains "account" or application keywords → mark verifyAccounts phases</li>
     *   <li>Otherwise → mark verifyIdentity phases (verifyCreate / verifyModify)</li>
     * </ul>
     * The <em>first</em> matching phase (in order) receives the failure indicator.
     */
    private static void markFailedPhases(IdentityReport idr, String failureMessage) {
        if (failureMessage == null || failureMessage.isEmpty()) return;

        boolean hasRoleFail = false;
        boolean hasAccountFail = false;
        boolean hasIdentityFail = false;

        for (String line : failureMessage.split("\n")) {
            line = line.trim();
            // Assertion message can be "Mismatch: <attr> on: key" (JSON mode)
            // or "Mismatch for sailpoint.<attr> on: key" (properties mode)
            String attrPrefix = null;
            if (line.startsWith("Mismatch: ")) {
                attrPrefix = "Mismatch: ";
            } else if (line.startsWith("Mismatch for ")) {
                attrPrefix = "Mismatch for ";
            }
            if (attrPrefix == null) continue;

            // Extract attribute name between prefix and " on: "
            int onIdx = line.indexOf(" on: ");
            if (onIdx < 0) continue;
            String attr = line.substring(attrPrefix.length(), onIdx).trim();

            if (attr.toLowerCase().contains("role")) {
                hasRoleFail = true;
            } else if (attr.toLowerCase().contains("account")
                    || attr.toLowerCase().contains("application")
                    || attr.toLowerCase().contains("app:")) {
                hasAccountFail = true;
            } else {
                hasIdentityFail = true;
            }
        }

        // Mark the first matching phase type as failed
        for (PhaseEntry phase : idr.phases) {
            String phaseType = classifyPhase(phase.name);
            boolean isVerifyType = phaseType.equals("verify")
                    && (phase.name.startsWith("verifyCreate")
                    || phase.name.startsWith("verifyModify")
                    || phase.name.startsWith("verify"));

            if (hasIdentityFail && isVerifyType) {
                phase.passed = false;
                hasIdentityFail = false; // mark only the first
            } else if (hasRoleFail && isVerifyType) {
                // verifyRoles is a detail item within verify phases; if we find
                // a phase with a "verifyRoles" detail, mark it
                boolean hasRoleDetail = phase.details.stream()
                        .anyMatch(d -> "verifyRoles".equals(d.category));
                if (hasRoleDetail) {
                    phase.passed = false;
                    hasRoleFail = false;
                }
            } else if (hasAccountFail && isVerifyType) {
                boolean hasAcctDetail = phase.details.stream()
                        .anyMatch(d -> "verifyAccounts".equals(d.category));
                if (hasAcctDetail) {
                    phase.passed = false;
                    hasAccountFail = false;
                }
            }
        }

        // Fallback 1: if any fail flag remains unhandled (phase had no details),
        // mark the last verify phase
        if (hasRoleFail || hasAccountFail || hasIdentityFail) {
            for (int i = idr.phases.size() - 1; i >= 0; i--) {
                PhaseEntry phase = idr.phases.get(i);
                if (classifyPhase(phase.name).equals("verify")) {
                    phase.passed = false;
                    break;
                }
            }
            return;
        }

        // Fallback 2: no "Mismatch for" lines were found at all (all flags
        // stayed false).  The message may still be a legitimate assertion error
        // in a different format (e.g. "expected [200] but found [500]").
        // Mark the last verify phase as failed when assertion keywords appear.
        if (failureMessage.contains("expected") && failureMessage.contains("but found")) {
            for (int i = idr.phases.size() - 1; i >= 0; i--) {
                PhaseEntry phase = idr.phases.get(i);
                if (classifyPhase(phase.name).equals("verify")) {
                    phase.passed = false;
                    break;
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Classify a phase name into a CSS class for icon/bar coloring. */
    private static String classifyPhase(String phaseName) {
        String lower = phaseName.toLowerCase();
        if (lower.contains("task") || lower.contains("workflow")) return "task";
        if (lower.startsWith("verify") || lower.startsWith("create")) return "verify";
        if (lower.startsWith("modify")) return "modify";
        if (lower.startsWith("delete")) return "delete";
        return "default";
    }

    /** Return an emoji icon character for a phase type. */
    private static String phaseIcon(String type) {
        return switch (type) {
            case "task"   -> "\u2699\uFE0F";  // ⚙️
            case "verify" -> "\u2705";         // ✅
            case "create" -> "\u2795";         // ➕
            case "modify" -> "\u270F\uFE0F";   // ✏️
            case "delete" -> "\u2796";         // ➖
            default       -> "\u25B6\uFE0F";   // ▶️
        };
    }

    /** Format milliseconds into a human-readable duration. */
    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long sec = ms / 1000;
        long millis = ms % 1000;
        if (sec < 60) return sec + "." + (millis / 100) + "s";
        long min = sec / 60;
        sec = sec % 60;
        return min + "m " + sec + "s";
    }

    /** Escape HTML entities in text content. */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
