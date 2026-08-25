package com.hackathon.insuranceportal.controller;

import com.hackathon.insuranceportal.entity.Claim;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.exception.ClaimNotFoundException;
import com.hackathon.insuranceportal.exception.PaymentDeclinedException;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import com.hackathon.insuranceportal.service.ClaimService;
import com.hackathon.insuranceportal.service.DocumentService;
import com.hackathon.insuranceportal.service.PaymentService;
import com.hackathon.insuranceportal.service.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * All customer-facing features from the flowchart:
 * My Policies, Claim Management, Premium Payments, Document Downloads.
 * Every action delegates to a service which is what actually writes logs -
 * this controller is just the web-facing wiring.
 */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final PolicyService policyService;
    private final ClaimService claimService;
    private final PaymentService paymentService;
    private final DocumentService documentService;

    public CustomerController(CustomerRepository customerRepository,
                               PolicyService policyService,
                               ClaimService claimService,
                               PaymentService paymentService,
                               DocumentService documentService) {
        this.customerRepository = customerRepository;
        this.policyService = policyService;
        this.claimService = claimService;
        this.paymentService = paymentService;
        this.documentService = documentService;
    }

    private Customer currentCustomer(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long customerId = (Long) session.getAttribute("customerId");
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Session customer no longer exists"));
    }

    @GetMapping("/home")
    public String home(HttpServletRequest request, Model model) {
        model.addAttribute("customer", currentCustomer(request));
        return "customer-home";
    }

    // ---------- 1. My Policies ----------

    @GetMapping("/policies")
    public String myPolicies(HttpServletRequest request, Model model) {
        Customer customer = currentCustomer(request);
        List<Policy> policies = policyService.viewPoliciesForCustomer(customer);
        model.addAttribute("customer", customer);
        model.addAttribute("policies", policies);
        return "customer/my-policies";
    }

    /** Simple self-service policy purchase, so there's always a policy to demo against. */
    @PostMapping("/policies/buy")
    public String buyPolicy(HttpServletRequest request,
                             @RequestParam String policyType,
                             @RequestParam BigDecimal premiumAmount) {
        Customer customer = currentCustomer(request);
        policyService.createPolicy(customer.getId(), policyType, premiumAmount, null);
        return "redirect:/customer/policies";
    }

    // ---------- 2. Claim Management ----------

    @GetMapping("/claims")
    public String claimManagement(HttpServletRequest request, Model model) {
        Customer customer = currentCustomer(request);
        model.addAttribute("customer", customer);
        model.addAttribute("claims", claimService.claimsForCustomer(customer));
        model.addAttribute("policies", policyService.listPoliciesForCustomer(customer));
        return "customer/claim-management";
    }

    @PostMapping("/claims/submit")
    public String submitClaim(HttpServletRequest request,
                               @RequestParam String policyId,
                               @RequestParam String claimType,
                               @RequestParam BigDecimal claimAmount,
                               Model model) {
        Customer customer = currentCustomer(request);
        // Only ever search within this customer's own policies - prevents
        // one customer from filing a claim against someone else's policy_id.
        Optional<Policy> policy = policyService.listPoliciesForCustomer(customer).stream()
                .filter(p -> p.getPolicyId().equals(policyId))
                .findFirst();

        if (policy.isPresent()) {
            claimService.submitClaim(policy.get(), customer, claimType, claimAmount);
        }
        return "redirect:/customer/claims";
    }

    @PostMapping("/claims/track")
    public String trackClaim(HttpServletRequest request,
                              @RequestParam String claimId,
                              Model model) {
        Customer customer = currentCustomer(request);
        model.addAttribute("customer", customer);
        model.addAttribute("claims", claimService.claimsForCustomer(customer));
        model.addAttribute("policies", policyService.listPoliciesForCustomer(customer));

        try {
            Claim claim = claimService.trackClaim(customer, claimId);
            model.addAttribute("trackedClaim", claim);
        } catch (ClaimNotFoundException e) {
            model.addAttribute("trackError", "No claim found with ID " + claimId);
        }
        return "customer/claim-management";
    }

    // ---------- 3. Premium Payments ----------

    @GetMapping("/payments")
    public String premiumPayments(HttpServletRequest request, Model model) {
        Customer customer = currentCustomer(request);
        model.addAttribute("customer", customer);
        model.addAttribute("policies", policyService.listPoliciesForCustomer(customer));
        model.addAttribute("payments", paymentService.paymentsForCustomer(customer));
        return "customer/premium-payments";
    }

    @PostMapping("/payments/pay")
    public String payPremium(HttpServletRequest request,
                              @RequestParam String policyId,
                              @RequestParam BigDecimal amount,
                              @RequestParam String paymentMethod,
                              Model model) {
        Customer customer = currentCustomer(request);
        // Restrict to this customer's own policies for the same reason as submitClaim above.
        Optional<Policy> policy = policyService.listPoliciesForCustomer(customer).stream()
                .filter(p -> p.getPolicyId().equals(policyId))
                .findFirst();

        model.addAttribute("customer", customer);
        model.addAttribute("policies", policyService.listPoliciesForCustomer(customer));

        if (policy.isPresent()) {
            try {
                paymentService.processPayment(customer, policy.get(), amount, paymentMethod, false);
                model.addAttribute("paymentSuccess", true);
            } catch (PaymentDeclinedException e) {
                model.addAttribute("paymentError", "Payment declined: " + e.getMessage());
            }
        }
        model.addAttribute("payments", paymentService.paymentsForCustomer(customer));
        return "customer/premium-payments";
    }

    // ---------- 4. Document Downloads ----------

    @GetMapping("/documents")
    public String documents(HttpServletRequest request, Model model) {
        Customer customer = currentCustomer(request);
        model.addAttribute("customer", customer);
        model.addAttribute("policies", policyService.listPoliciesForCustomer(customer));
        return "customer/documents";
    }

    @GetMapping("/documents/download/{policyId}")
    public ResponseEntity<byte[]> downloadCertificate(HttpServletRequest request,
                                                        @PathVariable String policyId) {
        Customer customer = currentCustomer(request);
        // Restrict to this customer's own policies - a customer should only
        // ever be able to download their own policy certificate.
        Policy policy = policyService.listPoliciesForCustomer(customer).stream()
                .filter(p -> p.getPolicyId().equals(policyId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Policy not found"));

        byte[] content = documentService.generatePolicyCertificate(customer, policy);
        String filename = "Policy_Certificate_" + policyId + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }
}
