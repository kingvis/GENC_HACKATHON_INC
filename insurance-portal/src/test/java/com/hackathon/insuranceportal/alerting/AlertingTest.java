package com.hackathon.insuranceportal.alerting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for the error-alerting scan + report - no Spring context and
 * no database, so they run instantly and never touch a real log file.
 *
 * The rule under test: ONLY the configured infrastructure error types trigger an
 * alert. Expected business failures - now logged as WARNING - must be ignored.
 */
class AlertingTest {

    // --- Infrastructure faults: these SHOULD be alerted. -------------------
    private static final String INFRA_GATEWAY_TIMEOUT =
            "{\"timestamp\":\"2026-09-09T10:00:00Z\",\"application_name\":\"PaymentService\",\"server_id\":\"payment-app-03\",\"correlation_id\":\"CORR-1\",\"event_type\":\"Gateway Timeout\",\"stage\":\"PREMIUM_COLLECTION\",\"status\":\"ERROR\",\"error\":\"Gateway Timeout\"}";
    private static final String INFRA_DB_FAILURE =
            "{\"timestamp\":\"2026-09-09T10:01:00Z\",\"application_name\":\"PolicyService\",\"server_id\":\"policy-app-01\",\"correlation_id\":null,\"event_type\":\"Database Connection Failure\",\"stage\":\"POLICY_LOOKUP\",\"status\":\"ERROR\",\"error\":\"Database Connection Failure\"}";

    // --- Business failures: now WARNING, these must NOT be alerted. ---------
    private static final String WARN_LOGIN =
            "{\"timestamp\":\"2026-09-09T10:02:00Z\",\"application_name\":\"CustomerPortal\",\"server_id\":\"customer-portal-01\",\"event_type\":\"LOGIN\",\"stage\":\"LOGIN\",\"status\":\"WARNING\",\"error\":\"INVALID_CREDENTIALS\"}";
    private static final String WARN_PAYMENT =
            "{\"timestamp\":\"2026-09-09T10:03:00Z\",\"application_name\":\"PaymentService\",\"server_id\":\"payment-app-04\",\"event_type\":\"Payment Failed\",\"stage\":\"PREMIUM_COLLECTION\",\"status\":\"WARNING\",\"error\":\"Transaction Error\"}";
    private static final String WARN_CLAIM =
            "{\"timestamp\":\"2026-09-09T10:04:00Z\",\"application_name\":\"CustomerPortal\",\"server_id\":\"customer-portal-01\",\"event_type\":\"CLAIM_TRACKING\",\"stage\":\"CLAIM_TRACKING\",\"status\":\"WARNING\",\"error\":\"CLAIM_NOT_FOUND\"}";
    private static final String WARN_SEARCH =
            "{\"timestamp\":\"2026-09-09T10:05:00Z\",\"application_name\":\"CustomerPortal\",\"server_id\":\"customer-portal-01\",\"event_type\":\"Customer Looked Up\",\"stage\":\"CUSTOMER_LOOKUP\",\"status\":\"WARNING\",\"error\":\"CUSTOMER_NOT_FOUND\"}";

    // --- Normal success. ----------------------------------------------------
    private static final String OK_POLICY =
            "{\"timestamp\":\"2026-09-09T10:06:00Z\",\"application_name\":\"PolicyService\",\"server_id\":\"policy-app-01\",\"event_type\":\"Policy Created\",\"stage\":\"POLICY_CREATION\",\"status\":\"SUCCESS\",\"error\":null}";

    private AlertingProperties propsFor(Path logFile) {
        AlertingProperties props = new AlertingProperties();
        props.setLogFilePath(logFile.toString());
        return props; // keeps the default six infrastructure error types
    }

    private void write(Path file, String... lines) throws IOException {
        Files.write(file, List.of(lines), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void detectsInfraErrorsAndIgnoresWarningsAndSuccess(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        write(log, OK_POLICY, WARN_LOGIN, INFRA_GATEWAY_TIMEOUT, WARN_PAYMENT, INFRA_DB_FAILURE);

        List<ErrorEvent> errors = new LogErrorScanner(propsFor(log)).scanForNewErrors();

        assertEquals(2, errors.size(), "only the two infrastructure errors should match");
        assertTrue(errors.stream().anyMatch(e -> e.error().equals("Gateway Timeout")));
        assertTrue(errors.stream().anyMatch(e -> e.error().equals("Database Connection Failure")));
    }

    @Test
    void allFourBusinessWarningsAreIgnored(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        write(log, WARN_LOGIN, WARN_PAYMENT, WARN_CLAIM, WARN_SEARCH);

        List<ErrorEvent> errors = new LogErrorScanner(propsFor(log)).scanForNewErrors();

        assertTrue(errors.isEmpty(),
                "invalid login, declined payment, claim-not-found and empty search must never alert");
    }

    @Test
    void everyConfiguredErrorTypeIsDetected(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        AlertingProperties props = propsFor(log);
        for (String type : props.getErrorTypes()) {
            write(log, "{\"application_name\":\"Svc\",\"event_type\":\"" + type
                    + "\",\"stage\":\"S\",\"status\":\"ERROR\",\"error\":\"" + type + "\"}");
        }

        List<ErrorEvent> errors = new LogErrorScanner(props).scanForNewErrors();

        assertEquals(props.getErrorTypes().size(), errors.size(),
                "each of the six configured infrastructure error types must be detected");
    }

    @Test
    void onlyReportsNewLinesOnRepeatScans(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        write(log, INFRA_GATEWAY_TIMEOUT);
        LogErrorScanner scanner = new LogErrorScanner(propsFor(log));

        assertEquals(1, scanner.scanForNewErrors().size(), "first scan sees the one error");
        assertEquals(0, scanner.scanForNewErrors().size(), "nothing new -> empty");

        write(log, INFRA_DB_FAILURE);
        List<ErrorEvent> second = scanner.scanForNewErrors();
        assertEquals(1, second.size(), "only the newly appended error is reported");
        assertEquals("Database Connection Failure", second.get(0).error());
    }

    @Test
    void skipsMalformedLinesWithoutFailing(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        write(log, "this is not json", "", INFRA_GATEWAY_TIMEOUT);

        List<ErrorEvent> errors = new LogErrorScanner(propsFor(log)).scanForNewErrors();

        assertEquals(1, errors.size(), "garbage and blank lines are skipped, the valid error still found");
    }

    @Test
    void reportGroupsByServiceAndCounts() {
        List<ErrorEvent> events = List.of(
                new ErrorEvent("t1", "PaymentService", "Gateway Timeout", "PREMIUM_COLLECTION", "ERROR", "Gateway Timeout", "CORR-1", "payment-app-03"),
                new ErrorEvent("t2", "PaymentService", "Payment Gateway Unavailable", "PREMIUM_COLLECTION", "ERROR", "Payment Gateway Unavailable", "CORR-2", "payment-app-02"),
                new ErrorEvent("t3", "PolicyService", "Database Connection Failure", "POLICY_LOOKUP", "ERROR", "Database Connection Failure", null, "policy-app-01"));

        ErrorReportBuilder builder = new ErrorReportBuilder();
        Map<String, List<ErrorEvent>> byService = builder.groupByService(events);

        assertEquals(2, byService.size());
        assertEquals(2, byService.get("PaymentService").size());
        assertEquals(1, byService.get("PolicyService").size());

        String subject = builder.subject(events);
        assertTrue(subject.contains("3 error event(s)"), subject);
        assertTrue(subject.contains("2 service(s)"), subject);

        String html = builder.buildHtml(events);
        assertTrue(html.contains("PaymentService"));
        assertTrue(html.contains("Gateway Timeout"));
        assertFalse(html.contains("INVALID_CREDENTIALS"), "warnings must not appear in the error report");
    }

    // ================= Warning-alert feature =================

    @Test
    void scanSeparatesErrorsFromWarnings(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        write(log, INFRA_GATEWAY_TIMEOUT, WARN_LOGIN, WARN_PAYMENT, OK_POLICY, WARN_CLAIM);

        ScanFindings f = new LogErrorScanner(propsFor(log)).scan();

        assertEquals(1, f.errors().size(), "one infrastructure error");
        assertEquals(3, f.warnings().size(), "three WARNING lines, success ignored");
    }

    @Test
    void warningAggregatorTriggersAtThresholdThenResets(@TempDir Path dir) {
        WarningAggregator agg = new WarningAggregator(propsFor(dir.resolve("app.log")));
        LocalDate day = LocalDate.of(2026, 9, 11);

        ThresholdAggregator.Check c1 = agg.addAndCheck(day, nWarnings(9), 10);
        assertFalse(c1.triggered(), "9 < 10 -> no email");
        assertEquals(9, c1.bufferedAfter());

        ThresholdAggregator.Check c2 = agg.addAndCheck(day, nWarnings(1), 10);
        assertTrue(c2.triggered(), "reaching 10 fires the report");
        assertEquals(10, c2.batch().size(), "the whole accumulated batch is emailed");
        assertEquals(0, c2.bufferedAfter(), "buffer cleared after firing");

        ThresholdAggregator.Check c3 = agg.addAndCheck(day, nWarnings(1), 10);
        assertFalse(c3.triggered(), "counting starts fresh for the next batch");
        assertEquals(1, c3.bufferedAfter());
    }

    @Test
    void warningAggregatorResetsOnNewDate(@TempDir Path dir) {
        WarningAggregator agg = new WarningAggregator(propsFor(dir.resolve("app.log")));

        agg.addAndCheck(LocalDate.of(2026, 9, 11), nWarnings(7), 10);
        ThresholdAggregator.Check next = agg.addAndCheck(LocalDate.of(2026, 9, 12), nWarnings(2), 10);

        assertFalse(next.triggered());
        assertEquals(2, next.bufferedAfter(), "a new day resets yesterday's count");
    }

    @Test
    void warningReportIsStyledAndDistinctFromError() {
        List<ErrorEvent> warns = List.of(
                warn("CustomerPortal", "INVALID_CREDENTIALS"),
                warn("PaymentService", "Transaction Error"));
        ErrorReportBuilder b = new ErrorReportBuilder();

        assertTrue(b.warningSubject(warns).contains("2 warning event(s)"));
        String html = b.buildWarningHtml(warns);
        assertTrue(html.contains("Warning Report"), "warning report title");
        assertTrue(html.contains("INVALID_CREDENTIALS"), "warning detail shown");
        assertTrue(html.contains("#b45309"), "amber accent for warnings");
    }

    @Test
    void errorReportOnlyFiresOnceTenErrorsAccumulate(@TempDir Path dir) {
        ErrorAggregator agg = new ErrorAggregator(propsFor(dir.resolve("app.log")));
        LocalDate day = LocalDate.of(2026, 9, 15);

        assertFalse(agg.addAndCheck(day, nErrors(9), 10).triggered(), "9 < 10 -> no error report yet");
        ThresholdAggregator.Check fire = agg.addAndCheck(day, nErrors(1), 10);
        assertTrue(fire.triggered(), "reaching 10 errors fires the Error Report");
        assertEquals(10, fire.batch().size(), "all 10 accumulated errors are in the batch");
        assertEquals(0, fire.bufferedAfter(), "buffer cleared after firing");
    }

    @Test
    void warningAndErrorAggregatorsUseSeparateState(@TempDir Path dir) {
        Path log = dir.resolve("app.log");
        WarningAggregator warns = new WarningAggregator(propsFor(log));
        ErrorAggregator errors = new ErrorAggregator(propsFor(log));

        // 9 of each on the same day: neither should fire, and they must not
        // pollute each other's count (separate sidecar files).
        assertFalse(warns.addAndCheck(LocalDate.of(2026, 9, 15), nWarnings(9), 10).triggered());
        assertFalse(errors.addAndCheck(LocalDate.of(2026, 9, 15), nErrors(9), 10).triggered());
        assertEquals(9, errors.addAndCheck(LocalDate.of(2026, 9, 15), List.of(), 10).bufferedAfter());
    }

    private List<ErrorEvent> nErrors(int n) {
        List<ErrorEvent> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new ErrorEvent("2026-09-15T10:00:00Z", "PaymentService", "Gateway Timeout",
                    "PREMIUM_COLLECTION", "ERROR", "Gateway Timeout", "C" + i, "payment-app-02"));
        }
        return list;
    }

    private ErrorEvent warn(String service, String error) {
        return new ErrorEvent("2026-09-11T10:00:00Z", service, error, "STAGE", "WARNING", error, null, "srv-01");
    }

    private List<ErrorEvent> nWarnings(int n) {
        List<ErrorEvent> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(warn("CustomerPortal", "INVALID_CREDENTIALS"));
        }
        return list;
    }
}
