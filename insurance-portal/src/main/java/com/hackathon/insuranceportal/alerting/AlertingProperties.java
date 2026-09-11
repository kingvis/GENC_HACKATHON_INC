package com.hackathon.insuranceportal.alerting;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All knobs for the error-log alerting feature, bound from {@code alerting.*}
 * in application.properties / .env. Nothing here is secret except the Resend
 * API key, which comes from the {@code RESEND_API_KEY} environment value.
 */
@ConfigurationProperties(prefix = "alerting")
public class AlertingProperties {

    /** Master switch. When false, no scans run and no mail is sent. */
    private boolean enabled = true;

    /** Log file to scan - defaults to the same file LogWriter writes to. */
    private String logFilePath = "logs/app.log";

    /** Scheduled scan interval in milliseconds. Default 600000 = 10 minutes. */
    private long scanIntervalMs = 600_000;

    /** Delay before the first scan after startup, in ms. Default 15000 = 15s. */
    private long initialDelayMs = 15_000;

    /** Resend REST endpoint. */
    private String resendUrl = "https://api.resend.com/emails";

    /** Resend API key (Bearer token). Set via RESEND_API_KEY in .env. */
    private String resendApiKey;

    /** Sender address. Test sender onboarding@resend.dev needs no domain setup. */
    private String mailFrom = "onboarding@resend.dev";

    /** Recipient(s). With the test sender this must be your Resend account email. */
    private List<String> mailTo = List.of();

    /**
     * The ONLY error types that trigger an error email. A log line is alertable
     * when its {@code error} or {@code event_type} matches (case-insensitive) one
     * of these. Everything else - including expected business failures logged as
     * WARNING (invalid login, declined payment, claim-not-found, empty customer
     * search) - is deliberately ignored so operators are only paged for genuine
     * infrastructure/system faults.
     */
    private List<String> errorTypes = List.of(
            "Claim Service Timeout",
            "Payment Gateway Unavailable",
            "Gateway Timeout",
            "Database Connection Failure",
            "Cache Server Unavailable",
            "Connection Pool Exhausted");

    /** When true, a scan that finds zero new errors sends no email. */
    private boolean skipEmailWhenNoErrors = true;

    /**
     * Warning-alert threshold. Warnings (expected business failures logged as
     * WARNING) are accumulated per calendar date; when a date's running count
     * reaches this many, a Warning Report email is sent and that batch is
     * cleared. Default 10. Set to 11 for strictly "more than 10".
     */
    private int warningThreshold = 10;

    /**
     * Hackathon fault simulator: when true, a scheduler periodically writes one
     * of the {@link #errorTypes} to the log so the scan+mail cycle has real
     * infrastructure errors to report. Turn off in a "clean" demo.
     */
    private boolean faultInjectionEnabled = true;

    /** How often the fault simulator injects one infra error. Default 5 min. */
    private long faultInjectionIntervalMs = 300_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getLogFilePath() { return logFilePath; }
    public void setLogFilePath(String logFilePath) { this.logFilePath = logFilePath; }

    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

    public String getResendUrl() { return resendUrl; }
    public void setResendUrl(String resendUrl) { this.resendUrl = resendUrl; }

    public String getResendApiKey() { return resendApiKey; }
    public void setResendApiKey(String resendApiKey) { this.resendApiKey = resendApiKey; }

    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }

    public List<String> getMailTo() { return mailTo; }
    public void setMailTo(List<String> mailTo) { this.mailTo = mailTo; }

    public List<String> getErrorTypes() { return errorTypes; }
    public void setErrorTypes(List<String> errorTypes) { this.errorTypes = errorTypes; }

    public boolean isSkipEmailWhenNoErrors() { return skipEmailWhenNoErrors; }
    public void setSkipEmailWhenNoErrors(boolean skipEmailWhenNoErrors) { this.skipEmailWhenNoErrors = skipEmailWhenNoErrors; }

    public int getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(int warningThreshold) { this.warningThreshold = warningThreshold; }

    public boolean isFaultInjectionEnabled() { return faultInjectionEnabled; }
    public void setFaultInjectionEnabled(boolean faultInjectionEnabled) { this.faultInjectionEnabled = faultInjectionEnabled; }

    public long getFaultInjectionIntervalMs() { return faultInjectionIntervalMs; }
    public void setFaultInjectionIntervalMs(long faultInjectionIntervalMs) { this.faultInjectionIntervalMs = faultInjectionIntervalMs; }
}
