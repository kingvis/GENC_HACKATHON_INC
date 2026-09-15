package com.hackathon.insuranceportal.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orchestrates one alert cycle: scan the log once, then handle two threshold-
 * gated outputs from that single pass -
 *   1. infrastructure ERRORS  -> accumulated per date; an Error Report is
 *                                emailed only once the error threshold is crossed;
 *   2. business WARNINGS       -> accumulated per date; a Warning Report is
 *                                emailed only once the warning threshold is crossed.
 * Both behave identically (see {@link ThresholdAggregator}): no email until at
 * least N of that kind have piled up, then one batched report with all of them.
 * Shared by the scheduler and the on-demand controller.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertingProperties props;
    private final LogErrorScanner scanner;
    private final ErrorAggregator errorAggregator;
    private final WarningAggregator warningAggregator;
    private final ErrorReportBuilder reportBuilder;
    private final ResendEmailClient emailClient;

    public AlertService(AlertingProperties props,
                        LogErrorScanner scanner,
                        ErrorAggregator errorAggregator,
                        WarningAggregator warningAggregator,
                        ErrorReportBuilder reportBuilder,
                        ResendEmailClient emailClient) {
        this.props = props;
        this.scanner = scanner;
        this.errorAggregator = errorAggregator;
        this.warningAggregator = warningAggregator;
        this.reportBuilder = reportBuilder;
        this.emailClient = emailClient;
    }

    /** Runs one scan-and-notify cycle and returns a short outcome summary. */
    public ScanResult scanAndNotify() {
        if (!props.isEnabled()) {
            log.debug("[alerting] Alerting disabled (alerting.enabled=false) - skipping scan");
            return new ScanResult(false, 0, 0, false, 0, 0, false, "alerting disabled");
        }

        ScanFindings findings = scanner.scan();
        List<ErrorEvent> errors = findings.errors();
        List<ErrorEvent> warnings = findings.warnings();

        Outcome err = handleErrors(errors);
        Outcome warn = handleWarnings(warnings);

        String detail = errors.size() + " new error(s), " + err.buffered + "/" + props.getErrorThreshold()
                + " buffered" + (err.emailed ? " [error report sent]" : "") + "; "
                + warnings.size() + " new warning(s), " + warn.buffered + "/" + props.getWarningThreshold()
                + " buffered" + (warn.emailed ? " [warning report sent]" : "");
        log.info("[alerting] {}", detail);
        return new ScanResult(true, errors.size(), err.buffered, err.emailed,
                warnings.size(), warn.buffered, warn.emailed, detail);
    }

    /** Accumulates errors per date; mails an Error Report only when the threshold is crossed. */
    private Outcome handleErrors(List<ErrorEvent> errors) {
        ThresholdAggregator.Check check = errorAggregator.addAndCheck(
                LocalDate.now(ZoneOffset.UTC), errors, props.getErrorThreshold());
        if (!check.triggered()) {
            return new Outcome(false, check.bufferedAfter());
        }
        boolean sent = emailClient.send(
                reportBuilder.subject(check.batch()),
                reportBuilder.buildHtml(check.batch()));
        return new Outcome(sent, 0);
    }

    /** Accumulates warnings per date; mails a Warning Report only when the threshold is crossed. */
    private Outcome handleWarnings(List<ErrorEvent> warnings) {
        ThresholdAggregator.Check check = warningAggregator.addAndCheck(
                LocalDate.now(ZoneOffset.UTC), warnings, props.getWarningThreshold());
        if (!check.triggered()) {
            return new Outcome(false, check.bufferedAfter());
        }
        boolean sent = emailClient.send(
                reportBuilder.warningSubject(check.batch()),
                reportBuilder.buildWarningHtml(check.batch()));
        return new Outcome(sent, 0);
    }

    private record Outcome(boolean emailed, int buffered) {
    }

    /** Outcome of a single scan cycle - returned to the HTTP caller as JSON. */
    public record ScanResult(boolean scanned,
                             int errorCount,
                             int errorsBuffered,
                             boolean errorEmailSent,
                             int warningCount,
                             int warningsBuffered,
                             boolean warningEmailSent,
                             String detail) {
    }
}
