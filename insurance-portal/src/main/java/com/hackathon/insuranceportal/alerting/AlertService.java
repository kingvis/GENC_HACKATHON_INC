package com.hackathon.insuranceportal.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orchestrates one alert cycle: scan the log once, then handle two independent
 * outputs from that single pass -
 *   1. infrastructure ERRORS  -> emailed immediately (any count), as before;
 *   2. business WARNINGS       -> accumulated per date and emailed only once the
 *                                 threshold is crossed (see {@link WarningAggregator}).
 * Shared by the scheduler and the on-demand controller so both behave identically.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertingProperties props;
    private final LogErrorScanner scanner;
    private final WarningAggregator warningAggregator;
    private final ErrorReportBuilder reportBuilder;
    private final ResendEmailClient emailClient;

    public AlertService(AlertingProperties props,
                        LogErrorScanner scanner,
                        WarningAggregator warningAggregator,
                        ErrorReportBuilder reportBuilder,
                        ResendEmailClient emailClient) {
        this.props = props;
        this.scanner = scanner;
        this.warningAggregator = warningAggregator;
        this.reportBuilder = reportBuilder;
        this.emailClient = emailClient;
    }

    /** Runs one scan-and-notify cycle and returns a short outcome summary. */
    public ScanResult scanAndNotify() {
        if (!props.isEnabled()) {
            log.debug("[alerting] Alerting disabled (alerting.enabled=false) - skipping scan");
            return new ScanResult(false, 0, false, 0, 0, false, "alerting disabled");
        }

        ScanFindings findings = scanner.scan();
        List<ErrorEvent> errors = findings.errors();
        List<ErrorEvent> warnings = findings.warnings();

        boolean errorEmailed = handleErrors(errors);
        WarningOutcome warn = handleWarnings(warnings);

        String detail = errors.size() + " error(s) [email " + (errorEmailed ? "sent" : "not sent") + "]; "
                + warnings.size() + " new warning(s), " + warn.buffered + " buffered"
                + (warn.emailed ? " [warning report sent]" : "");
        log.info("[alerting] {}", detail);
        return new ScanResult(true, errors.size(), errorEmailed,
                warnings.size(), warn.buffered, warn.emailed, detail);
    }

    /** Mails infrastructure errors immediately (unchanged behaviour). */
    private boolean handleErrors(List<ErrorEvent> errors) {
        if (errors.isEmpty()) {
            log.info("[alerting] Scan complete - no new error events found");
            if (props.isSkipEmailWhenNoErrors()) {
                return false;
            }
            return emailClient.send("[Insurance Portal] No new error events",
                    "<p>No new error events since the last scan.</p>");
        }
        return emailClient.send(reportBuilder.subject(errors), reportBuilder.buildHtml(errors));
    }

    /** Accumulates warnings; mails a Warning Report only when the threshold is crossed. */
    private WarningOutcome handleWarnings(List<ErrorEvent> warnings) {
        WarningAggregator.WarningCheck check = warningAggregator.addAndCheck(
                LocalDate.now(ZoneOffset.UTC), warnings, props.getWarningThreshold());

        if (!check.triggered()) {
            return new WarningOutcome(false, check.bufferedAfter());
        }
        boolean sent = emailClient.send(
                reportBuilder.warningSubject(check.batch()),
                reportBuilder.buildWarningHtml(check.batch()));
        return new WarningOutcome(sent, 0);
    }

    private record WarningOutcome(boolean emailed, int buffered) {
    }

    /** Outcome of a single scan cycle - returned to the HTTP caller as JSON. */
    public record ScanResult(boolean scanned,
                             int errorCount,
                             boolean errorEmailSent,
                             int warningCount,
                             int warningsBuffered,
                             boolean warningEmailSent,
                             String detail) {
    }
}
