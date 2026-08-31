package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Agent;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.exception.InvalidCredentialsException;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.AgentRepository;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles login for both roles and self-service customer registration.
 * Every attempt - success or failure - produces exactly one log line.
 */
@Service
public class AuthService {

    private static final String APPLICATION_NAME = "CustomerPortal";
    private static final String SERVER_ID = "customer-portal-01";

    private final CustomerRepository customerRepository;
    private final AgentRepository agentRepository;
    private final LogWriter logWriter;

    public AuthService(CustomerRepository customerRepository,
                        AgentRepository agentRepository,
                        LogWriter logWriter) {
        this.customerRepository = customerRepository;
        this.agentRepository = agentRepository;
        this.logWriter = logWriter;
    }

    /**
     * Registers a new customer with just a username and password, matching
     * the team's decision to keep customer signup simple (no email/OTP).
     * correlation_id stays null until this customer's first policy exists.
     */
    public Customer register(String username, String password, String fullName) {
        if (customerRepository.existsByUsername(username)) {
            throw new InvalidCredentialsException("USERNAME_TAKEN", "Username already exists");
        }
        Customer customer = new Customer();
        customer.setUsername(username);
        customer.setPassword(password);
        customer.setFullName(fullName);
        return customerRepository.save(customer);
    }

    /**
     * Registers a new agent. Agents sign in with an email, so the email is the
     * unique credential; a human-readable agent code (used in log lines, e.g.
     * "agent-742") is generated automatically and guaranteed unique.
     */
    public Agent registerAgent(String email, String password, String fullName) {
        if (agentRepository.existsByEmail(email)) {
            throw new InvalidCredentialsException("EMAIL_TAKEN", "An agent with that email already exists");
        }
        Agent agent = new Agent();
        agent.setEmail(email);
        agent.setPassword(password);
        agent.setFullName(fullName);
        agent.setAgentCode(generateUniqueAgentCode());
        return agentRepository.save(agent);
    }

    /** Generates an "agent-<n>" code that isn't already taken. */
    private String generateUniqueAgentCode() {
        String code;
        do {
            code = "agent-" + ThreadLocalRandom.current().nextInt(100, 10_000);
        } while (agentRepository.existsByAgentCode(code));
        return code;
    }

    public Customer loginCustomer(String username, String password, String sessionId,
                                   String ipAddress, String channel) {
        long start = System.currentTimeMillis();
        Optional<Customer> found = customerRepository.findByUsername(username);

        if (found.isEmpty() || !found.get().getPassword().equals(password)) {
            Long customerId = found.map(Customer::getId).orElse(null);
            logWriter.write(new LogEvent()
                    .applicationName(APPLICATION_NAME)
                    .serverId(SERVER_ID)
                    .customerId(customerId)
                    .eventType("LOGIN")
                    .stage("LOGIN")
                    .status("FAILED")
                    .durationMs(System.currentTimeMillis() - start)
                    .error("INVALID_CREDENTIALS")
                    .extra("session_id", (Object) null)
                    .extra("ip_address", ipAddress)
                    .extra("channel", channel)
                    .extra("message", "Customer login failed"));
            throw new InvalidCredentialsException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        Customer customer = found.get();
        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customer.getId())
                .eventType("LOGIN")
                .stage("LOGIN")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("session_id", sessionId)
                .extra("ip_address", ipAddress)
                .extra("channel", channel)
                .extra("customer_segment", customer.getCustomerSegment())
                .extra("message", "Customer login successful"));

        return customer;
    }

    public Agent loginAgent(String email, String password) {
        long start = System.currentTimeMillis();
        Optional<Agent> found = agentRepository.findByEmail(email);

        if (found.isEmpty() || !found.get().getPassword().equals(password)) {
            throw new InvalidCredentialsException("INVALID_CREDENTIALS", "Invalid email or password");
        }
        return found.get();
    }

    /** Generates a lightweight session identifier for the HTTP session. */
    public String newSessionId() {
        return "SESS-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
