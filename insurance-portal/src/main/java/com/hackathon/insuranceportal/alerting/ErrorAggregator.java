package com.hackathon.insuranceportal.alerting;

import org.springframework.stereotype.Component;

/**
 * Accumulates infrastructure ERROR events per date and fires an Error Report
 * only once the daily count crosses {@link AlertingProperties#getErrorThreshold()}
 * - mirroring how warnings are handled, so no email is sent until at least the
 * threshold number of errors have piled up. All logic lives in
 * {@link ThresholdAggregator}; this just binds the error state file and label.
 */
@Component
public class ErrorAggregator extends ThresholdAggregator {

    public ErrorAggregator(AlertingProperties props) {
        super(props, ".errorstate", "Error");
    }
}
