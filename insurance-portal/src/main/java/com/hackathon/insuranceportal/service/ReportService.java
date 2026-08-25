package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.ClaimRepository;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import com.hackathon.insuranceportal.repository.PaymentRepository;
import com.hackathon.insuranceportal.repository.PolicyRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backs the agent-facing "Reports" feature: simple counts across
 * customers, policies, claims, and payments. Deliberately simple
 * aggregates - a hackathon prototype doesn't need a real BI layer, it
 * needs the reports screen to be real and to generate a log line.
 */
@Service
public class ReportService {

    private static final String APPLICATION_NAME = "CustomerPortal";
    private static final String SERVER_ID = "customer-portal-01";

    private final CustomerRepository customerRepository;
    private final PolicyRepository policyRepository;
    private final ClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final LogWriter logWriter;

    public ReportService(CustomerRepository customerRepository,
                          PolicyRepository policyRepository,
                          ClaimRepository claimRepository,
                          PaymentRepository paymentRepository,
                          LogWriter logWriter) {
        this.customerRepository = customerRepository;
        this.policyRepository = policyRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.logWriter = logWriter;
    }

    public Map<String, Object> generateSummaryReport(String agentId) {
        long start = System.currentTimeMillis();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCustomers", customerRepository.count());
        report.put("totalPolicies", policyRepository.count());
        report.put("activePolicies", policyRepository.countByStatus("ACTIVE"));
        report.put("totalClaims", claimRepository.count());
        report.put("openClaims", claimRepository.countByClaimStatus("OPEN"));
        report.put("settledClaims", claimRepository.countByClaimStatus("SETTLED"));
        report.put("totalPayments", paymentRepository.count());
        report.put("failedPayments", paymentRepository.countByStatus("FAILED"));

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(null)
                .eventType("Report Generated")
                .stage("REPORT_GENERATION")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("agent_id", agentId)
                .extra("report_type", "SUMMARY"));

        return report;
    }
}
