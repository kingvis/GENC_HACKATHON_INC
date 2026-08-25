package com.hackathon.insuranceportal.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single structured log line, built fluently and handed to LogWriter.
 *
 * The common envelope fields (timestamp, application_name, server_id,
 * environment, correlation_id, customer_id, policy_id, claim_id, event_type,
 * stage, status, duration_ms, error) match the shared schema used across
 * customer-service, claim-service, payment-service, and policy-service.
 *
 * Anything beyond that common envelope (session_id, ip_address, amount,
 * payment_method, claim_type, ...) is added via extra(key, value) and gets
 * merged into the same flat JSON object, matching how the reference logs
 * lay out service-specific fields alongside the common ones.
 *
 * Usage:
 *   LogEvent event = new LogEvent()
 *       .applicationName("PaymentService")
 *       .serverId("payment-app-01")
 *       .customerId(customerId)
 *       .policyId(policy.getPolicyId())
 *       .claimId(null)
 *       .eventType("Payment Successful")
 *       .stage("PREMIUM_COLLECTION")
 *       .status("SUCCESS")
 *       .durationMs(542)
 *       .error(null)
 *       .extra("amount", 4500.00)
 *       .extra("payment_method", "CREDIT_CARD");
 *   logWriter.write(event);
 */
public class LogEvent {

    private String applicationName;
    private String serverId;
    private Long customerId;
    private String policyId;
    private String claimId;
    private String eventType;
    private String stage;
    private String status;
    private long durationMs;
    private String error;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public LogEvent applicationName(String v) { this.applicationName = v; return this; }
    public LogEvent serverId(String v) { this.serverId = v; return this; }
    public LogEvent customerId(Long v) { this.customerId = v; return this; }
    public LogEvent policyId(String v) { this.policyId = v; return this; }
    public LogEvent claimId(String v) { this.claimId = v; return this; }
    public LogEvent eventType(String v) { this.eventType = v; return this; }
    public LogEvent stage(String v) { this.stage = v; return this; }
    public LogEvent status(String v) { this.status = v; return this; }
    public LogEvent durationMs(long v) { this.durationMs = v; return this; }
    public LogEvent error(String v) { this.error = v; return this; }
    public LogEvent extra(String key, Object value) { this.extra.put(key, value); return this; }

    public String getApplicationName() { return applicationName; }
    public String getServerId() { return serverId; }
    public Long getCustomerId() { return customerId; }
    public String getPolicyId() { return policyId; }
    public String getClaimId() { return claimId; }
    public String getEventType() { return eventType; }
    public String getStage() { return stage; }
    public String getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public Map<String, Object> getExtra() { return extra; }
}
