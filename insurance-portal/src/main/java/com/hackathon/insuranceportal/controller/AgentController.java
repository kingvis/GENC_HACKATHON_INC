package com.hackathon.insuranceportal.controller;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.exception.ClaimNotFoundException;
import com.hackathon.insuranceportal.exception.PolicyNotFoundException;
import com.hackathon.insuranceportal.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * All agent-facing features from the flowchart:
 * Customer Management, Policy Management, Premium Payment, Claim Review,
 * Reports. As with CustomerController, the actual business logic and
 * logging live in the service layer - this is just the web wiring.
 */
@Controller
@RequestMapping("/agent")
public class AgentController {

    private final CustomerLookupService customerLookupService;
    private final PolicyService policyService;
    private final ClaimService claimService;
    private final PaymentService paymentService;
    private final ReportService reportService;

    public AgentController(CustomerLookupService customerLookupService,
                            PolicyService policyService,
                            ClaimService claimService,
                            PaymentService paymentService,
                            ReportService reportService) {
        this.customerLookupService = customerLookupService;
        this.policyService = policyService;
        this.claimService = claimService;
        this.paymentService = paymentService;
        this.reportService = reportService;
    }

    private String currentAgentId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return (String) session.getAttribute("agentId");
    }

    @GetMapping("/home")
    public String home(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        model.addAttribute("agentName", session.getAttribute("agentName"));
        return "agent-home";
    }

    // ---------- 1. Customer Management ----------

    @GetMapping("/customers")
    public String customerManagement(HttpServletRequest request, Model model) {
        model.addAttribute("customers", customerLookupService.allCustomers());
        return "agent/customer-management";
    }

    @PostMapping("/customers/search")
    public String searchCustomers(HttpServletRequest request,
                                   @RequestParam String query,
                                   Model model) {
        String agentId = currentAgentId(request);
        List<Customer> results = customerLookupService.searchCustomers(query, agentId);
        model.addAttribute("customers", results);
        model.addAttribute("searchQuery", query);
        return "agent/customer-management";
    }

    // ---------- 2. Policy Management ----------

    @GetMapping("/policies")
    public String policyManagement(Model model) {
        model.addAttribute("policies", policyService.allPolicies());
        model.addAttribute("customers", customerLookupService.allCustomers());
        return "agent/policy-management";
    }

    @PostMapping("/policies/create")
    public String createPolicy(HttpServletRequest request,
                                @RequestParam Long customerId,
                                @RequestParam String policyType,
                                @RequestParam BigDecimal premiumAmount) {
        String agentId = currentAgentId(request);
        policyService.createPolicy(customerId, policyType, premiumAmount, agentId);
        return "redirect:/agent/policies";
    }

    @PostMapping("/policies/{policyId}/approve")
    public String approvePolicy(HttpServletRequest request, @PathVariable String policyId) {
        policyService.approvePolicy(policyId, currentAgentId(request));
        return "redirect:/agent/policies";
    }

    @PostMapping("/policies/{policyId}/renew")
    public String renewPolicy(HttpServletRequest request, @PathVariable String policyId) {
        policyService.renewPolicy(policyId, currentAgentId(request));
        return "redirect:/agent/policies";
    }

    // ---------- 3. Premium Payment (agent-recorded) ----------

    @GetMapping("/payments")
    public String premiumPayment(Model model) {
        model.addAttribute("policies", policyService.allPolicies());
        model.addAttribute("payments", paymentService.allPayments());
        return "agent/premium-payment";
    }

    @PostMapping("/payments/record")
    public String recordPayment(@RequestParam String policyId,
                                 @RequestParam BigDecimal amount,
                                 Model model) {
        Optional<Policy> policy = policyService.allPolicies().stream()
                .filter(p -> p.getPolicyId().equals(policyId))
                .findFirst();

        if (policy.isPresent()) {
            paymentService.processPayment(policy.get().getCustomer(), policy.get(), amount, "MANUAL", true);
        }
        return "redirect:/agent/payments";
    }

    // ---------- 4. Claim Review ----------

    @GetMapping("/claims")
    public String claimReview(Model model) {
        model.addAttribute("claims", claimService.allClaims());
        return "agent/claim-review";
    }

    @PostMapping("/claims/{claimId}/review")
    public String reviewClaim(HttpServletRequest request, @PathVariable String claimId) {
        try {
            claimService.reviewClaim(claimId, currentAgentId(request));
        } catch (ClaimNotFoundException ignored) {
            // Claim id no longer valid; simply fall back to the list view.
        }
        return "redirect:/agent/claims";
    }

    @PostMapping("/claims/{claimId}/approve")
    public String approveClaim(HttpServletRequest request, @PathVariable String claimId) {
        claimService.approveClaim(claimId, currentAgentId(request));
        return "redirect:/agent/claims";
    }

    @PostMapping("/claims/{claimId}/reject")
    public String rejectClaim(HttpServletRequest request, @PathVariable String claimId) {
        claimService.rejectClaim(claimId, currentAgentId(request));
        return "redirect:/agent/claims";
    }

    @PostMapping("/claims/{claimId}/settle")
    public String settleClaim(HttpServletRequest request, @PathVariable String claimId) {
        claimService.settleClaim(claimId, currentAgentId(request));
        return "redirect:/agent/claims";
    }

    // ---------- 5. Reports ----------

    @GetMapping("/reports")
    public String reports(HttpServletRequest request, Model model) {
        model.addAttribute("report", reportService.generateSummaryReport(currentAgentId(request)));
        return "agent/reports";
    }
}
