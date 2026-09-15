package com.hackathon.insuranceportal.alerting;

import org.springframework.stereotype.Component;

/**
 * Accumulates business WARNING events per date and fires a Warning Report once
 * the daily count crosses {@link AlertingProperties#getWarningThreshold()}.
 * All logic lives in {@link ThresholdAggregator}; this just binds the warning
 * state file and label.
 */
@Component
public class WarningAggregator extends ThresholdAggregator {

    public WarningAggregator(AlertingProperties props) {
        super(props, ".warnstate", "Warning");
    }
}
