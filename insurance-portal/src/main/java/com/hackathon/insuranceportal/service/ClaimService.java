package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Claim;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.exception.ClaimNotFoundException;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.ClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Handles claim submission and tracking (customer-facing) plus review,
 * approval, rejection, and settlement (agent-facing). Every state change
 * writes its own log line so a claim's full lifecycle can be reconstructed
 * from the log stream alone.
 */
@Service
public class ClaimService {

    private static final String APPLICATION_NAME = "ClaimService";
    private static final String SERVER_ID = "claims-app-01";

    private final ClaimRepository claimRepository;
    private final LogWriter logWriter;

    public ClaimService(ClaimRepository claimRepository, LogWriter logWriter) {
        this.claimRepository = claimRepository;
        this.logWriter = logWriter;
    }

    @Transactional
    public Claim submitClaim(Policy policy, Customer customer, String claimType, BigDecimal claimAmount) {
        long start = System.currentTimeMillis();

        Claim claim = new Claim();
        claim.setClaimId(com.hackathon.insuranceportal.util.IdGenerator.nextClaimId());
        claim.setPolicy(policy);
        claim.setCustomer(customer);
        claim.setClaimType(claimType);
        claim.setClaimAmount(claimAmount);
        Claim saved = claimRepository.save(claim);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customer.getId())
                .policyId(policy.getPolicyId())
                .claimId(saved.getClaimId())
                .eventType("Claim Submitted")
                .stage("CLAIM_SUBMISSION")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("claim_type", claimType)
                .extra("claim_amount", claimAmount)
                .extra("claim_status", saved.getClaimStatus())
                .extra("claim_decision", saved.getClaimDecision()));

        return saved;
    }

    /** Customer checking status of their claim ("Claim Management" feature). */
    public Claim trackClaim(Customer customer, String claimId) {
        long start = System.currentTimeMillis();
        Optional<Claim> found = claimRepository.findByClaimId(claimId);

        if (found.isEmpty()) {
            logWriter.write(new LogEvent()
                    .applicationName("CustomerPortal")
                    .serverId("customer-portal-01")
                    .customerId(customer.getId())
                    .claimId(claimId)
                    .eventType("CLAIM_TRACKING")
                    .stage("CLAIM_TRACKING")
                    .status("WARNING")
                    .durationMs(System.currentTimeMillis() - start)
                    .error("CLAIM_NOT_FOUND")
                    .extra("message", "Claim status fetch failed"));
            throw new ClaimNotFoundException("Claim not found: " + claimId);
        }

        Claim claim = found.get();
        logWriter.write(new LogEvent()
                .applicationName("CustomerPortal")
                .serverId("customer-portal-01")
                .customerId(customer.getId())
                .policyId(claim.getPolicy().getPolicyId())
                .claimId(claim.getClaimId())
                .eventType("CLAIM_TRACKING")
                .stage("CLAIM_TRACKING")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("claim_status", claim.getClaimStatus())
                .extra("message", "Claim status fetched successfully"));

        return claim;
    }

    @Transactional
    public Claim reviewClaim(String claimId, String agentId) {
        return transitionClaim(claimId, agentId, "UNDER_REVIEW", "PENDING",
                "Claim Under Review", "CLAIM_REVIEW");
    }

    @Transactional
    public Claim approveClaim(String claimId, String agentId) {
        return transitionClaim(claimId, agentId, "APPROVED", "APPROVED",
                "Claim Approved", "CLAIM_APPROVAL");
    }

    @Transactional
    public Claim rejectClaim(String claimId, String agentId) {
        return transitionClaim(claimId, agentId, "REJECTED", "REJECTED",
                "Claim Rejected", "CLAIM_REJECTION");
    }

    @Transactional
    public Claim settleClaim(String claimId, String agentId) {
        return transitionClaim(claimId, agentId, "SETTLED", "APPROVED",
                "Claim Settled", "CLAIM_SETTLEMENT");
    }

    private Claim transitionClaim(String claimId, String agentId, String newStatus,
                                   String newDecision, String eventType, String stage) {
        long start = System.currentTimeMillis();
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found: " + claimId));

        claim.setClaimStatus(newStatus);
        claim.setClaimDecision(newDecision);
        claim.setAgentId(agentId);
        Claim saved = claimRepository.save(claim);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(saved.getCustomer().getId())
                .policyId(saved.getPolicy().getPolicyId())
                .claimId(saved.getClaimId())
                .eventType(eventType)
                .stage(stage)
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("claim_status", saved.getClaimStatus())
                .extra("claim_decision", saved.getClaimDecision())
                .extra("agent_id", agentId));

        return saved;
    }

    public List<Claim> allClaims() {
        return claimRepository.findAll();
    }

    public List<Claim> claimsForCustomer(Customer customer) {
        return claimRepository.findByCustomerOrderBySubmittedAtDesc(customer);
    }
}
