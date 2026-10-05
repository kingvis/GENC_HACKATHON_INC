package com.hackathon.insuranceportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hackathon.insuranceportal.alerting.AlertingProperties;

/**
 * Entry point for the Insurance Portal prototype.
 *
 * Run this class directly from your IDE, or from the command line with:
 *   mvn spring-boot:run
 *
 * Once started, the application is available at http://localhost:8080
 *
 * {@code @EnableScheduling} turns on the fixed-interval error-log scan in the
 * {@code alerting} package (see AlertScheduler).
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AlertingProperties.class)
public class InsurancePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsurancePortalApplication.class, args);
    }

}
