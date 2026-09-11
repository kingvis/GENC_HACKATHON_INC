package com.hackathon.insuranceportal.alerting;

import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hackathon fault simulator.
 *
 * Real infrastructure faults (a timed-out gateway, an exhausted connection pool)
 * are hard to reproduce on demand, so this scheduler injects them: on a fixed
 * interval it writes ONE genuine infrastructure error - drawn from the same list
 * the scanner alerts on ({@link AlertingProperties#getErrorTypes()}) - straight
 * into the structured log the {@link LogErrorScanner} reads. The normal scan +
 * email cycle then reports it like any other error.
 *
 * It cycles through the faults round-robin so every type shows up over time,
 * rather than randomly starving some. Controlled by
 * {@code alerting.fault-injection-enabled} / {@code alerting.fault-injection-interval-ms}.
 *
 * Business "warnings" (bad login, declined payment, claim-not-found, empty
 * search) are NOT injected here - they are produced by real user actions and are
 * deliberately excluded from alerting.
 */
@Component
public class FaultInjectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FaultInjectionScheduler.class);

    /** One simulated infrastructure fault and the service it belongs to. */
    private record Fault(String service, String serverId, String stage, String errorType) {
    }

    /**
     * The six infrastructure faults. Each {@code errorType} matches an entry in
     * {@link AlertingProperties#getErrorTypes()} so the scanner flags it.
     */
    private static final List<Fault> FAULTS = List.of(
            new Fault("ClaimService",   "claims-app-01",        "CLAIM_PROCESSING",  "Claim Service Timeout"),
            new Fault("PaymentService", "payment-app-02",       "PREMIUM_COLLECTION", "Payment Gateway Unavailable"),
            new Fault("PaymentService", "payment-app-03",       "PREMIUM_COLLECTION", "Gateway Timeout"),
            new Fault("PolicyService",  "policy-app-01",        "POLICY_LOOKUP",      "Database Connection Failure"),
            new Fault("CustomerPortal", "customer-portal-01",   "SESSION",            "Cache Server Unavailable"),
            new Fault("PolicyService",  "policy-app-01",        "DATA_ACCESS",        "Connection Pool Exhausted"));

    private final AlertingProperties props;
    private final LogWriter logWriter;
    private final AtomicInteger cursor = new AtomicInteger(0);

    public FaultInjectionScheduler(AlertingProperties props, LogWriter logWriter) {
        this.props = props;
        this.logWriter = logWriter;
    }

    @Scheduled(fixedDelayString = "${alerting.fault-injection-interval-ms:300000}",
               initialDelayString = "${alerting.fault-injection-interval-ms:300000}")
    public void injectFault() {
        if (!props.isFaultInjectionEnabled()) {
            return;
        }

        Fault fault = FAULTS.get(Math.floorMod(cursor.getAndIncrement(), FAULTS.size()));

        logWriter.write(new LogEvent()
                .applicationName(fault.service())
                .serverId(fault.serverId())
                .customerId(null)              // infra fault - not tied to a customer
                .eventType(fault.errorType())
                .stage(fault.stage())
                .status("ERROR")
                .durationMs(ThreadLocalRandom.current().nextInt(3000, 30000))
                .error(fault.errorType())
                .extra("message", "Simulated infrastructure fault: " + fault.errorType()));

        log.info("[alerting] Injected simulated fault '{}' for {}", fault.errorType(), fault.service());
    }
}
