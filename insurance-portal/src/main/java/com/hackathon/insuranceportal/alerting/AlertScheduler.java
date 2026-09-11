package com.hackathon.insuranceportal.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires a scan on a fixed interval. Interval is {@code alerting.scan-interval-ms},
 * and the first scan runs {@code alerting.initial-delay-ms} after startup (so a
 * restart produces a report quickly rather than waiting a full interval).
 * {@code fixedDelayString} means the next run starts this long after the
 * previous one <em>finishes</em>, so scans never overlap even if one runs long.
 *
 * The scheduled trigger and the on-demand endpoint both call the same
 * {@link AlertService}, so "schedule vs dynamic" is purely how it's kicked off.
 */
@Component
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertService alertService;

    public AlertScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${alerting.scan-interval-ms:600000}",
               initialDelayString = "${alerting.initial-delay-ms:15000}")
    public void scheduledScan() {
        log.debug("[alerting] Scheduled scan starting");
        alertService.scanAndNotify();
    }
}
