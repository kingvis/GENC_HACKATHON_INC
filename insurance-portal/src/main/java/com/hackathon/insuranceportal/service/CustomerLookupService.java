package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs the agent-facing "Customer Management" feature: search for any
 * customer and view their details. Logged under CustomerPortal since it's
 * still a customer-record lookup, just performed by an agent.
 */
@Service
public class CustomerLookupService {

    private static final String APPLICATION_NAME = "CustomerPortal";
    private static final String SERVER_ID = "customer-portal-01";

    private final CustomerRepository customerRepository;
    private final LogWriter logWriter;

    public CustomerLookupService(CustomerRepository customerRepository, LogWriter logWriter) {
        this.customerRepository = customerRepository;
        this.logWriter = logWriter;
    }

    public List<Customer> searchCustomers(String query, String agentId) {
        long start = System.currentTimeMillis();
        List<Customer> results = customerRepository
                .findByFullNameContainingIgnoreCaseOrUsernameContainingIgnoreCase(query, query);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(results.isEmpty() ? null : results.get(0).getId())
                .eventType("Customer Looked Up")
                .stage("CUSTOMER_LOOKUP")
                .status(results.isEmpty() ? "WARNING" : "SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(results.isEmpty() ? "CUSTOMER_NOT_FOUND" : null)
                .extra("agent_id", agentId)
                .extra("query", query)
                .extra("result_count", results.size()));

        return results;
    }

    public List<Customer> allCustomers() {
        return customerRepository.findAll();
    }
}
