package com.hackathon.insuranceportal.config;

import com.hackathon.insuranceportal.entity.Agent;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.repository.AgentRepository;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import com.hackathon.insuranceportal.service.ClaimService;
import com.hackathon.insuranceportal.service.PolicyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds a small amount of demo data on first startup, so the app is
 * immediately usable without manual setup. Runs only if the database is
 * empty - safe to restart the app repeatedly without duplicating data.
 *
 * Demo credentials (see README for the full list):
 *   Customer: jane.doe / password123   (has an existing policy + claim)
 *   Customer: new.customer / password123  (fresh account, no policy yet)
 *   Agent:    agent@company.com / agent123
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final AgentRepository agentRepository;
    private final PolicyService policyService;
    private final ClaimService claimService;

    public DataSeeder(CustomerRepository customerRepository,
                       AgentRepository agentRepository,
                       PolicyService policyService,
                       ClaimService claimService) {
        this.customerRepository = customerRepository;
        this.agentRepository = agentRepository;
        this.policyService = policyService;
        this.claimService = claimService;
    }

    @Override
    public void run(String... args) {
        if (agentRepository.count() == 0) {
            Agent agent = new Agent();
            agent.setEmail("agent@company.com");
            agent.setPassword("agent123");
            agent.setFullName("Alex Agent");
            agent.setAgentCode("agent-411");
            agentRepository.save(agent);
        }

        if (customerRepository.count() == 0) {
            Customer jane = new Customer();
            jane.setUsername("jane.doe");
            jane.setPassword("password123");
            jane.setFullName("Jane Doe");
            jane.setCustomerSegment("VIP");
            jane = customerRepository.save(jane);

            var policy = policyService.createPolicy(jane.getId(), "HEALTH", new BigDecimal("15400.00"), "agent-411");
            claimService.submitClaim(policy, jane, "Hospitalization", new BigDecimal("8500.00"));

            Customer newCustomer = new Customer();
            newCustomer.setUsername("new.customer");
            newCustomer.setPassword("password123");
            newCustomer.setFullName("Sam Newcomer");
            newCustomer.setCustomerSegment("STANDARD");
            customerRepository.save(newCustomer);

            System.out.println("=================================================");
            System.out.println(" Demo data seeded. Login credentials:");
            System.out.println("   Customer (with policy/claim): jane.doe / password123");
            System.out.println("   Customer (fresh account):     new.customer / password123");
            System.out.println("   Agent:                        agent@company.com / agent123");
            System.out.println("=================================================");
        }
    }
}
