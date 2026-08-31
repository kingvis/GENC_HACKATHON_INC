package com.hackathon.insuranceportal.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.insuranceportal.entity.Claim;
import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Payment;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.repository.ClaimRepository;
import com.hackathon.insuranceportal.repository.CustomerRepository;
import com.hackathon.insuranceportal.repository.PaymentRepository;
import com.hackathon.insuranceportal.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * One-off importer that loads the historical reference event logs
 * (logs/combined_service.log) into the customers/policies/claims/payments
 * tables, so a fresh install has realistic data to work with.
 *
 * It is OPT-IN and does nothing unless you ask for it:
 *
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--app.import-logs=true
 *   # or, on a built jar:
 *   java -jar target/insurance-portal.jar --app.import-logs=true
 *
 * It reuses the exact same database connection the app already reads from your
 * .env (SUPABASE_DB_URL / USER / PASSWORD) - no separate credentials or keys.
 *
 * What it does with the raw event stream:
 *   - collapses the many log events per entity down to one row (a claim's
 *     lifecycle events become one claim row in its latest state);
 *   - synthesizes the fields the logs don't carry (a login username/password
 *     so imported customers can sign in, a policy's premium taken from its
 *     premium payment, start/renewal dates, etc.);
 *   - is collision-safe: if an imported correlation_id or claim_id already
 *     exists (e.g. the seeded demo data uses one), it is remapped to a free
 *     value instead of clashing;
 *   - is idempotent: if it detects the data is already imported, it skips.
 *
 * Runs AFTER {@link DataSeeder} (see @Order) so the demo accounts are seeded
 * first and preserved.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.import-logs", havingValue = "true")
public class LogImporter implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final PolicyRepository policyRepository;
    private final ClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Where to read the reference log from. Default assumes you run from the
     *  insurance-portal/ folder, with the logs one level up at ../logs/. */
    @Value("${app.import-logs-file:}")
    private String configuredFile;

    public LogImporter(CustomerRepository customerRepository,
                       PolicyRepository policyRepository,
                       ClaimRepository claimRepository,
                       PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.policyRepository = policyRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Path file = resolveLogFile();
        if (file == null) {
            System.err.println("[LogImporter] Could not find combined_service.log - tried the configured "
                    + "path, ../logs/, and logs/. Set --app.import-logs-file=<path> and try again. Skipping.");
            return;
        }

        List<JsonNode> events = readEvents(file);
        System.out.println("[LogImporter] Read " + events.size() + " log events from " + file);

        // ---- idempotency: if the first imported customer already exists, assume done ----
        List<CustAcc> customers = buildCustomers(events);
        if (customers.isEmpty()) {
            System.out.println("[LogImporter] No customer events found - nothing to import.");
            return;
        }
        String firstUsername = usernameFor(customers.get(0).code);
        if (customerRepository.existsByUsername(firstUsername)) {
            System.out.println("[LogImporter] Looks already imported (" + firstUsername
                    + " exists) - skipping. Nothing changed.");
            return;
        }

        // ---- gather existing unique keys so imports never collide ----
        Set<String> usedCorr = new HashSet<>();
        Set<String> usedClaimIds = new HashSet<>();
        for (Customer c : customerRepository.findAll()) {
            if (c.getCorrelationId() != null) usedCorr.add(c.getCorrelationId());
        }
        for (Claim c : claimRepository.findAll()) usedClaimIds.add(c.getClaimId());

        // ================= CUSTOMERS =================
        Map<String, Customer> custByCode = new LinkedHashMap<>();
        int corrRemaps = 0;
        List<Customer> toSaveCustomers = new ArrayList<>();
        int corrSeq = 201; // free-suffix cursor, references use 1..200
        for (CustAcc acc : customers) {
            Customer c = new Customer();
            c.setUsername(usernameFor(acc.code));
            c.setPassword("password123");
            c.setFullName("Customer " + numberPart(acc.code));
            c.setCustomerSegment(acc.segment != null ? acc.segment : "STANDARD");
            c.setCreatedAt(acc.created != null ? acc.created : LocalDateTime.now());
            String corr = acc.corr;
            if (corr == null || usedCorr.contains(corr)) {
                while (usedCorr.contains(String.format("CORR-2026-%06d", corrSeq))) corrSeq++;
                corr = String.format("CORR-2026-%06d", corrSeq++);
                corrRemaps++;
            }
            usedCorr.add(corr);
            c.setCorrelationId(corr);
            toSaveCustomers.add(c);
            custByCode.put(acc.code, c);
        }
        customerRepository.saveAll(toSaveCustomers);

        // ================= POLICIES =================
        Map<String, BigDecimal> premiums = premiumsByPolicy(events);
        Map<String, Policy> polByCode = new LinkedHashMap<>();
        List<Policy> toSavePolicies = new ArrayList<>();
        for (PolAcc acc : buildPolicies(events)) {
            Customer owner = custByCode.get(acc.custCode);
            if (owner == null) continue; // no owner in this import batch - skip defensively
            Policy p = new Policy();
            p.setPolicyId(acc.code);
            p.setCustomer(owner);
            p.setAgentId(acc.agentId);
            p.setPolicyType(acc.type != null ? acc.type : "AUTO");
            p.setPremiumAmount(premiums.getOrDefault(acc.code, new BigDecimal("5000.00")));
            p.setStatus(acc.docGenerated ? "ACTIVE" : (acc.anyFailed ? "REJECTED" : "PENDING_APPROVAL"));
            LocalDate start = (acc.created != null ? acc.created : LocalDateTime.now()).toLocalDate();
            p.setStartDate(start);
            p.setRenewalDate(start.plusYears(1));
            toSavePolicies.add(p);
            polByCode.put(acc.code, p);
        }
        policyRepository.saveAll(toSavePolicies);

        // ================= CLAIMS (collapsed to latest state) =================
        int claimRemaps = 0;
        List<Claim> toSaveClaims = new ArrayList<>();
        int claimSeq = 201;
        for (ClmAcc acc : buildClaims(events)) {
            Policy pol = polByCode.get(acc.policyCode);
            Customer cust = custByCode.get(acc.custCode);
            if (pol == null || cust == null) continue;
            String claimId = acc.code;
            if (usedClaimIds.contains(claimId)) {
                while (usedClaimIds.contains(String.format("CLM-2026-%06d", claimSeq))) claimSeq++;
                claimId = String.format("CLM-2026-%06d", claimSeq++);
                claimRemaps++;
            }
            usedClaimIds.add(claimId);
            Claim cl = new Claim();
            cl.setClaimId(claimId);
            cl.setPolicy(pol);
            cl.setCustomer(cust);
            cl.setClaimType(acc.type != null ? acc.type : "General");
            cl.setClaimAmount(acc.amount != null ? acc.amount : BigDecimal.ZERO);
            cl.setClaimStatus(acc.status != null ? acc.status : "OPEN");
            cl.setClaimDecision(acc.decision != null ? acc.decision : "PENDING");
            cl.setAgentId(acc.agentId);
            cl.setSubmittedAt(acc.submittedAt != null ? acc.submittedAt : LocalDateTime.now());
            toSaveClaims.add(cl);
        }
        claimRepository.saveAll(toSaveClaims);

        // ================= PAYMENTS =================
        List<Payment> toSavePayments = new ArrayList<>();
        for (PayAcc acc : buildPayments(events)) {
            Policy pol = polByCode.get(acc.policyCode);
            Customer cust = custByCode.get(acc.custCode);
            if (pol == null || cust == null) continue;
            Payment pay = new Payment();
            pay.setPaymentId(acc.paymentId);
            pay.setTransactionId(acc.transactionId);
            pay.setPolicy(pol);
            pay.setCustomer(cust);
            pay.setAmount(acc.amount != null ? acc.amount : BigDecimal.ZERO);
            pay.setPaymentMethod(acc.method != null ? acc.method : "UNKNOWN");
            pay.setStatus(acc.status != null ? acc.status : "SUCCESS");
            pay.setGateway(acc.gateway);
            pay.setFailureReason(acc.failureReason);
            pay.setPaidAt(acc.paidAt != null ? acc.paidAt : LocalDateTime.now());
            toSavePayments.add(pay);
        }
        paymentRepository.saveAll(toSavePayments);

        System.out.println("=================================================");
        System.out.println(" [LogImporter] Import complete:");
        System.out.println("   customers : " + toSaveCustomers.size() + " (correlation_id remaps: " + corrRemaps + ")");
        System.out.println("   policies  : " + toSavePolicies.size());
        System.out.println("   claims    : " + toSaveClaims.size() + " (claim_id remaps: " + claimRemaps + ")");
        System.out.println("   payments  : " + toSavePayments.size());
        System.out.println(" Imported customers log in with: cust-<code> / password123");
        System.out.println("=================================================");
    }

    // ------------------------------------------------------------------ file

    private Path resolveLogFile() {
        List<String> candidates = new ArrayList<>();
        if (configuredFile != null && !configuredFile.isBlank()) candidates.add(configuredFile);
        candidates.add("../logs/combined_service.log");
        candidates.add("logs/combined_service.log");
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private List<JsonNode> readEvents(Path file) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    out.add(mapper.readTree(line));
                } catch (Exception ignore) {
                    // skip a malformed line rather than fail the whole import
                }
            }
        }
        return out;
    }

    // --------------------------------------------------------------- helpers

    private static String text(JsonNode n, String field) {
        return (n.hasNonNull(field)) ? n.get(field).asText() : null;
    }

    private static LocalDateTime ts(JsonNode n) {
        String t = text(n, "timestamp");
        if (t == null) return null;
        try {
            return OffsetDateTime.parse(t).toLocalDateTime();
        } catch (Exception e) {
            try { return LocalDateTime.parse(t.replace("Z", "")); } catch (Exception e2) { return null; }
        }
    }

    private static String usernameFor(String custCode) {
        return "cust-" + numberPart(custCode).toLowerCase();
    }

    private static String numberPart(String code) {
        int dash = code.lastIndexOf('-');
        return dash >= 0 ? code.substring(dash + 1) : code;
    }

    // ---------------------------------------------------------- accumulators

    private static class CustAcc { String code; String segment; String corr; LocalDateTime created; }
    private static class PolAcc { String code; String type; String agentId; String custCode; boolean docGenerated; boolean anyFailed; LocalDateTime created; }
    private static class ClmAcc { String code; String policyCode; String custCode; String type; BigDecimal amount; String status; String decision; String agentId; LocalDateTime submittedAt; }
    private static class PayAcc { String paymentId; String transactionId; String policyCode; String custCode; BigDecimal amount; String method; String status; String gateway; String failureReason; LocalDateTime paidAt; }

    private List<CustAcc> buildCustomers(List<JsonNode> events) {
        Map<String, CustAcc> map = new LinkedHashMap<>();
        for (JsonNode e : events) {
            String code = text(e, "customer_id");
            if (code == null) continue;
            CustAcc a = map.computeIfAbsent(code, k -> { CustAcc x = new CustAcc(); x.code = k; return x; });
            if (a.segment == null) a.segment = text(e, "customer_segment");
            if (a.corr == null) a.corr = text(e, "correlation_id");
            LocalDateTime t = ts(e);
            if (t != null && (a.created == null || t.isBefore(a.created))) a.created = t;
        }
        return new ArrayList<>(map.values());
    }

    private Map<String, BigDecimal> premiumsByPolicy(List<JsonNode> events) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (JsonNode e : events) {
            if ("Payment Successful".equals(text(e, "event_type"))
                    && "PREMIUM_COLLECTION".equals(text(e, "stage"))
                    && e.hasNonNull("policy_id") && e.hasNonNull("amount")) {
                map.putIfAbsent(e.get("policy_id").asText(), e.get("amount").decimalValue());
            }
        }
        return map;
    }

    private List<PolAcc> buildPolicies(List<JsonNode> events) {
        Map<String, PolAcc> map = new LinkedHashMap<>();
        Map<String, LocalDateTime> createdEvent = new HashMap<>();
        for (JsonNode e : events) {
            if (!"PolicyService".equals(text(e, "application_name"))) continue;
            String code = text(e, "policy_id");
            if (code == null) continue;
            PolAcc a = map.computeIfAbsent(code, k -> { PolAcc x = new PolAcc(); x.code = k; return x; });
            if (a.type == null) a.type = text(e, "policy_type");
            if (a.agentId == null) a.agentId = text(e, "agent_id");
            if (a.custCode == null) a.custCode = text(e, "customer_id");
            String et = text(e, "event_type");
            if ("Document Generated".equals(et)) a.docGenerated = true;
            if ("FAILED".equals(text(e, "status"))) a.anyFailed = true;
            LocalDateTime t = ts(e);
            if (t != null) {
                if ("Policy Created".equals(et)) {
                    LocalDateTime prev = createdEvent.get(code);
                    if (prev == null || t.isBefore(prev)) createdEvent.put(code, t);
                }
                if (a.created == null || t.isBefore(a.created)) a.created = t;
            }
        }
        for (PolAcc a : map.values()) {
            LocalDateTime created = createdEvent.get(a.code);
            if (created != null) a.created = created;
        }
        return new ArrayList<>(map.values());
    }

    private List<ClmAcc> buildClaims(List<JsonNode> events) {
        Map<String, List<JsonNode>> byClaim = new LinkedHashMap<>();
        for (JsonNode e : events) {
            if (!"ClaimService".equals(text(e, "application_name"))) continue;
            String code = text(e, "claim_id");
            if (code == null) continue;
            byClaim.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
        }
        List<ClmAcc> out = new ArrayList<>();
        for (Map.Entry<String, List<JsonNode>> en : byClaim.entrySet()) {
            List<JsonNode> evs = en.getValue();
            evs.sort(Comparator.comparing(x -> {
                String t = text(x, "timestamp");
                return t == null ? "" : t;
            }));
            JsonNode last = evs.get(evs.size() - 1);
            ClmAcc a = new ClmAcc();
            a.code = en.getKey();
            a.policyCode = text(last, "policy_id");
            a.custCode = text(last, "customer_id");
            a.type = text(last, "claim_type");
            a.amount = last.hasNonNull("claim_amount") ? last.get("claim_amount").decimalValue() : null;
            a.status = text(last, "claim_status");
            a.decision = text(last, "claim_decision");
            for (JsonNode e : evs) {
                if (a.agentId == null) a.agentId = text(e, "agent_id");
                if ("Claim Submitted".equals(text(e, "event_type"))) a.submittedAt = ts(e);
            }
            if (a.submittedAt == null) a.submittedAt = ts(evs.get(0));
            out.add(a);
        }
        return out;
    }

    private List<PayAcc> buildPayments(List<JsonNode> events) {
        Map<String, PayAcc> map = new LinkedHashMap<>();
        for (JsonNode e : events) {
            if (!"PaymentService".equals(text(e, "application_name"))) continue;
            String pid = text(e, "payment_id");
            if (pid == null || map.containsKey(pid)) continue;
            PayAcc a = new PayAcc();
            a.paymentId = pid;
            a.transactionId = text(e, "transaction_id");
            a.policyCode = text(e, "policy_id");
            a.custCode = text(e, "customer_id");
            a.amount = e.hasNonNull("amount") ? e.get("amount").decimalValue() : null;
            a.method = text(e, "payment_method");
            a.status = text(e, "status");
            a.gateway = e.path("details").path("gateway").asText(null);
            a.failureReason = text(e, "error");
            a.paidAt = ts(e);
            map.put(pid, a);
        }
        return new ArrayList<>(map.values());
    }
}
