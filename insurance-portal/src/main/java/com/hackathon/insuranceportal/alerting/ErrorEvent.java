package com.hackathon.insuranceportal.alerting;

/**
 * One error log line, flattened to just the fields the report needs.
 * {@code service} is the log's {@code application_name} - the thing we group by.
 */
public record ErrorEvent(
        String timestamp,
        String service,
        String eventType,
        String stage,
        String status,
        String error,
        String correlationId,
        String serverId) {
}
