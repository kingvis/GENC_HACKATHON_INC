package com.hackathon.insuranceportal.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes one structured JSON log line per user action, to a shared log file
 * that a downstream monitoring application reads/tails.
 *
 * The one thing every caller must understand: correlation_id is NOT
 * generated here. It is looked up from the Customer record every time, and
 * is only ever set once - the moment a customer's first policy is created
 * (see PolicyService). Until then it will be null in the logs, which
 * correctly reflects that this customer has no traceable history yet.
 *
 * This class deliberately swallows its own I/O failures rather than letting
 * a logging problem break the actual customer/agent action. A prototype
 * that fails a real request because of a logging hiccup would defeat the
 * purpose - logging should observe the app, never break it.
 */
@Component
public class LogWriter {

    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path logFilePath;

    public LogWriter(CustomerRepository customerRepository,
                      @Value("${app.log-file-path:logs/app.log}") String logFilePath) {
        this.customerRepository = customerRepository;
        this.logFilePath = Path.of(logFilePath);
        ensureLogDirectoryExists();
    }

    private void ensureLogDirectoryExists() {
        try {
            Path parent = logFilePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[LogWriter] Could not create log directory: " + e.getMessage());
        }
    }

    /**
     * Writes the given event as one JSON line. Looks up correlation_id from
     * the customer record automatically - callers never set it directly.
     */
    public void write(LogEvent event) {
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("timestamp", Instant.now().toString());
            line.put("application_name", event.getApplicationName());
            line.put("server_id", event.getServerId());
            line.put("environment", "PROTOTYPE");
            line.put("correlation_id", resolveCorrelationId(event.getCustomerId()));
            line.put("customer_id", event.getCustomerId() == null ? null : "CUST-" + event.getCustomerId());
            line.put("policy_id", event.getPolicyId());
            line.put("claim_id", event.getClaimId());
            line.put("event_type", event.getEventType());
            line.put("stage", event.getStage());
            line.put("status", event.getStatus());
            line.put("duration_ms", event.getDurationMs());
            line.put("error", event.getError());
            line.putAll(event.getExtra());

            appendLine(objectMapper.writeValueAsString(line));
        } catch (Exception e) {
            // A logging failure must never break the caller's actual request.
            System.err.println("[LogWriter] Failed to write log line: " + e.getMessage());
        }
    }

    private String resolveCorrelationId(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .map(Customer::getCorrelationId)
                .orElse(null);
    }

    private synchronized void appendLine(String jsonLine) {
        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(logFilePath,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND))) {
            writer.println(jsonLine);
        } catch (IOException e) {
            System.err.println("[LogWriter] Failed to append to log file: " + e.getMessage());
        }
    }
}
