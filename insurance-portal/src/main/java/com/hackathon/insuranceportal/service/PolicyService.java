package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.exception.PolicyNotFoundException;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import com.hackathon.insuranceportal.repository.PolicyRepository;
import com.hackathon.insuranceportal.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Handles policy creation (by an agent, or self-service by a customer),
 * approval, renewal, and lookup. This is also where a customer's
 * correlation_id gets generated for the first time, if they don't have
 * one yet - see createPolicy().
 */
@Service
public class PolicyService {

    private static final String APPLICATION_NAME = "PolicyService";
    private static final String SERVER_ID = "policy-app-01";

    private final PolicyRepository policyRepository;
    private final CustomerRepository customerRepository;
    private final LogWriter logWriter;

    public PolicyService(PolicyRepository policyRepository,
                          CustomerRepository customerRepository,
                          LogWriter logWriter) {
        this.policyRepository = policyRepository;
        this.customerRepository = customerRepository;
        this.logWriter = logWriter;
    }

    /**
     * Creates a new policy for a customer. If this customer has no
     * correlation_id yet (i.e. this is their very first policy), one is
     * generated here and saved onto the Customer record before any log
     * line is written - so even this very first log already carries it.
     */
    @Transactional
    public Policy createPolicy(Long customerId, String policyType, BigDecimal premiumAmount, String agentId) {
        long start = System.currentTimeMillis();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new PolicyNotFoundException("Customer not found: " + customerId));

        if (customer.getCorrelationId() == null) {
            customer.setCorrelationId(IdGenerator.nextCorrelationId());
            customerRepository.save(customer);
        }

        Policy policy = new Policy();
        policy.setPolicyId(IdGenerator.nextPolicyId());
        policy.setCustomer(customer);
        policy.setPolicyType(policyType);
        policy.setPremiumAmount(premiumAmount);
        policy.setAgentId(agentId);
        policy.setStatus(agentId != null ? "ACTIVE" : "PENDING_APPROVAL");
        Policy saved = policyRepository.save(policy);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customerId)
                .policyId(saved.getPolicyId())
                .claimId(null)
                .eventType("Policy Created")
                .stage("POLICY_CREATION")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("proposal_id", IdGenerator.nextProposalId())
                .extra("policy_type", policyType)
                .extra("agent_id", agentId));

        return saved;
    }

    @Transactional
    public Policy approvePolicy(String policyId, String agentId) {
        long start = System.currentTimeMillis();
        Policy policy = policyRepository.findByPolicyId(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));

        policy.setStatus("ACTIVE");
        policy.setAgentId(agentId);
        Policy saved = policyRepository.save(policy);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(saved.getCustomer().getId())
                .policyId(saved.getPolicyId())
                .eventType("Policy Approved")
                .stage("POLICY_APPROVAL")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("policy_type", saved.getPolicyType())
                .extra("agent_id", agentId));

        return saved;
    }

    @Transactional
    public Policy renewPolicy(String policyId, String agentId) {
        long start = System.currentTimeMillis();
        Policy policy = policyRepository.findByPolicyId(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));

        policy.setRenewalDate(LocalDate.now().plusYears(1));
        policy.setStatus("RENEWED");
        Policy saved = policyRepository.save(policy);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(saved.getCustomer().getId())
                .policyId(saved.getPolicyId())
                .eventType("Policy Renewed")
                .stage("RENEWAL_PROCESSING")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("policy_type", saved.getPolicyType())
                .extra("agent_id", agentId)
                .extra("new_renewal_date", saved.getRenewalDate().toString()));

        return saved;
    }

    /**
     * Customer viewing their own policy list ("My Policies" feature).
     * This is the ONLY method that writes a "Policy Viewed" log line -
     * intentionally, so that log only fires when a customer genuinely
     * opens the My Policies screen, not every time some other screen
     * needs the list internally to populate a dropdown.
     */
    public List<Policy> viewPoliciesForCustomer(Customer customer) {
        long start = System.currentTimeMillis();
        List<Policy> policies = listPoliciesForCustomer(customer);

        String primaryPolicyId = policies.isEmpty() ? null : policies.get(0).getPolicyId();

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customer.getId())
                .policyId(primaryPolicyId)
                .eventType("Policy Viewed")
                .stage("POLICY_LOOKUP")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("policy_count", policies.size()));

        return policies;
    }

    /**
     * Same data as viewPoliciesForCustomer, but silent - no log line.
     * Use this whenever a controller just needs the list internally
     * (e.g. to populate a dropdown on the claims or payments page)
     * rather than as the customer explicitly viewing their policies.
     */
    public List<Policy> listPoliciesForCustomer(Customer customer) {
        return policyRepository.findByCustomerOrderByStartDateDesc(customer);
    }

    public Optional<Policy> findPrimaryPolicy(Customer customer) {
        return policyRepository.findFirstByCustomerOrderByIdDesc(customer);
    }

    public List<Policy> allPolicies() {
        return policyRepository.findAll();
    }
}
