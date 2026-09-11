package com.hackathon.insuranceportal.controller;

import com.hackathon.insuranceportal.alerting.AlertService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * On-demand ("dynamic") trigger for the error-log scan, alongside the
 * fixed-interval scheduled scan. Lets you fire a scan immediately, e.g.:
 *
 *   curl -X POST http://localhost:8080/internal/alerts/scan
 */
@RestController
@RequestMapping("/internal/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/scan")
    public AlertService.ScanResult scanNow() {
        return alertService.scanAndNotify();
    }
}
