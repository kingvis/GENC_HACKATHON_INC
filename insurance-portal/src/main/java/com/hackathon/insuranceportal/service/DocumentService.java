package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Generates a simple plain-text "policy certificate" for download. A real
 * product would render a PDF - for this prototype, a text file is enough
 * to prove the download flow works and produces a correct log line.
 */
@Service
public class DocumentService {

    private static final String APPLICATION_NAME = "CustomerPortal";
    private static final String SERVER_ID = "customer-portal-01";

    private final LogWriter logWriter;

    public DocumentService(LogWriter logWriter) {
        this.logWriter = logWriter;
    }

    public byte[] generatePolicyCertificate(Customer customer, Policy policy) {
        long start = System.currentTimeMillis();

        String content = """
                =========================================
                        POLICY CERTIFICATE
                =========================================
                Policy ID:      %s
                Policy Type:    %s
                Customer:       %s
                Premium:        %s
                Status:         %s
                Start Date:     %s
                Renewal Date:   %s
                Generated on:   %s
                =========================================
                This is a system-generated document for
                prototype/demo purposes only.
                =========================================
                """.formatted(
                policy.getPolicyId(),
                policy.getPolicyType(),
                customer.getFullName(),
                policy.getPremiumAmount(),
                policy.getStatus(),
                policy.getStartDate(),
                policy.getRenewalDate(),
                java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customer.getId())
                .policyId(policy.getPolicyId())
                .eventType("Document Downloaded")
                .stage("DOCUMENT_DOWNLOAD")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("file_type", "TXT")
                .extra("document_name", "Policy_Certificate_" + policy.getPolicyId() + ".txt"));

        return bytes;
    }
}
