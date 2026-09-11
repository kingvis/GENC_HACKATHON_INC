package com.hackathon.insuranceportal.alerting;

import java.util.List;

/**
 * The outcome of a single log scan: the infrastructure {@code errors} (which are
 * mailed immediately) and the business {@code warnings} (which are accumulated
 * and only mailed once a daily threshold is crossed). Both lists cover only the
 * new lines since the previous scan.
 */
public record ScanFindings(List<ErrorEvent> errors, List<ErrorEvent> warnings) {

    public static ScanFindings empty() {
        return new ScanFindings(List.of(), List.of());
    }
}
