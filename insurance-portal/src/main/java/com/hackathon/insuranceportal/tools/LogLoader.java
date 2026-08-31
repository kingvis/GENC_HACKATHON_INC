package com.hackathon.insuranceportal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * STANDALONE one-time utility - NOT part of the running application.
 *
 * Loads a historical reference event log (JSON-lines) into the
 * customers/policies/claims/payments tables. It is meant to be run by hand,
 * once, when you want to seed an existing database with sample data. The
 * portal itself never calls this - it lives here only so it can be run with
 * the same dependencies and the same .env database credentials.
 *
 * Usage (from the insurance-portal/ folder, so it can read .env):
 *
 *   mvn compile exec:java -Dexec.args="../logs/combined_service.log"
 *
 * The single argument is the path to the log file to load - you supply it.
 * Database connection is read from .env (SUPABASE_DB_URL / USER / PASSWORD),
 * falling back to environment variables of the same names.
 *
 * Behaviour:
 *   - collapses the raw event stream to one row per entity (a claim's whole
 *     lifecycle -> one claim row in its latest state);
 *   - synthesizes fields the logs don't carry (imported customers log in as
 *     cust-<code> / password123, premium taken from the premium payment, etc.);
 *   - is collision-safe: remaps a correlation_id / claim_id that already exists;
 *   - is idempotent: detects an already-loaded dataset and stops.
 *   - runs in a single transaction (all-or-nothing).
 */
public final class LogLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LogLoader() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.out.println("Usage: provide the path to the log file to load, e.g.");
            System.out.println("  mvn compile exec:java -Dexec.args=\"../logs/combined_service.log\"");
            return;
        }
        Path file = Path.of(args[0]);
        if (!Files.isRegularFile(file)) {
            System.err.println("[LogLoader] File not found: " + file.toAbsolutePath());
            return;
        }

        Map<String, String> cfg = loadEnv();
        String url = cfg.get("SUPABASE_DB_URL");
        String user = cfg.get("SUPABASE_DB_USER");
        String pass = cfg.get("SUPABASE_DB_PASSWORD");
        if (url == null || user == null || pass == null) {
            System.err.println("[LogLoader] Missing DB settings. Ensure SUPABASE_DB_URL / SUPABASE_DB_USER / "
                    + "SUPABASE_DB_PASSWORD are set in .env (run from the insurance-portal/ folder) or the environment.");
            return;
        }

        try {
            List<JsonNode> events = readEvents(file);
            System.out.println("[LogLoader] Read " + events.size() + " log events from " + file);
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                conn.setAutoCommit(false);
                try {
                    load(conn, events);
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("[LogLoader] FAILED (rolled back): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------- load

    private static void load(Connection conn, List<JsonNode> events) throws Exception {
        List<CustAcc> customers = buildCustomers(events);
        if (customers.isEmpty()) {
            System.out.println("[LogLoader] No customer events found - nothing to load.");
            return;
        }

        // idempotency: if the first imported customer already exists, stop.
        String firstUsername = usernameFor(customers.get(0).code);
        if (exists(conn, "SELECT 1 FROM customers WHERE username = ?", firstUsername)) {
            System.out.println("[LogLoader] Looks already loaded (" + firstUsername
                    + " exists) - stopping. Nothing changed.");
            return;
        }

        Set<String> usedCorr = queryStrings(conn, "SELECT correlation_id FROM customers WHERE correlation_id IS NOT NULL");
        Set<String> usedClaimIds = queryStrings(conn, "SELECT claim_id FROM claims");

        // ---------- customers ----------
        Map<String, Long> custIdByCode = new LinkedHashMap<>();
        int corrRemaps = 0, corrSeq = 201;
        String cSql = "INSERT INTO customers (username,password,full_name,correlation_id,customer_segment,created_at) "
                + "VALUES (?,?,?,?,?,?) RETURNING id";
        for (CustAcc a : customers) {
            String corr = a.corr;
            if (corr == null || usedCorr.contains(corr)) {
                while (usedCorr.contains(String.format("CORR-2026-%06d", corrSeq))) corrSeq++;
                corr = String.format("CORR-2026-%06d", corrSeq++);
                corrRemaps++;
            }
            usedCorr.add(corr);
            try (PreparedStatement ps = conn.prepareStatement(cSql)) {
                ps.setString(1, usernameFor(a.code));
                ps.setString(2, "password123");
                ps.setString(3, "Customer " + numberPart(a.code));
                ps.setString(4, corr);
                ps.setString(5, a.segment != null ? a.segment : "STANDARD");
                ps.setTimestamp(6, Timestamp.valueOf(a.created != null ? a.created : LocalDateTime.now()));
                custIdByCode.put(a.code, returnedId(ps));
            }
        }

        // ---------- policies ----------
        Map<String, BigDecimal> premiums = premiumsByPolicy(events);
        Map<String, Long> polIdByCode = new LinkedHashMap<>();
        String pSql = "INSERT INTO policies (policy_id,customer_id,agent_id,policy_type,premium_amount,status,start_date,renewal_date) "
                + "VALUES (?,?,?,?,?,?,?,?) RETURNING id";
        int policyCount = 0;
        for (PolAcc a : buildPolicies(events)) {
            Long custId = custIdByCode.get(a.custCode);
            if (custId == null) continue;
            LocalDate start = (a.created != null ? a.created : LocalDateTime.now()).toLocalDate();
            try (PreparedStatement ps = conn.prepareStatement(pSql)) {
                ps.setString(1, a.code);
                ps.setLong(2, custId);
                ps.setString(3, a.agentId);
                ps.setString(4, a.type != null ? a.type : "AUTO");
                ps.setBigDecimal(5, premiums.getOrDefault(a.code, new BigDecimal("5000.00")));
                ps.setString(6, a.docGenerated ? "ACTIVE" : (a.anyFailed ? "REJECTED" : "PENDING_APPROVAL"));
                ps.setDate(7, java.sql.Date.valueOf(start));
                ps.setDate(8, java.sql.Date.valueOf(start.plusYears(1)));
                polIdByCode.put(a.code, returnedId(ps));
            }
            policyCount++;
        }

        // ---------- claims ----------
        int claimRemaps = 0, claimSeq = 201, claimCount = 0;
        String clSql = "INSERT INTO claims (claim_id,policy_id,customer_id,claim_type,claim_amount,claim_status,claim_decision,agent_id,submitted_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        for (ClmAcc a : buildClaims(events)) {
            Long polId = polIdByCode.get(a.policyCode);
            Long custId = custIdByCode.get(a.custCode);
            if (polId == null || custId == null) continue;
            String claimId = a.code;
            if (usedClaimIds.contains(claimId)) {
                while (usedClaimIds.contains(String.format("CLM-2026-%06d", claimSeq))) claimSeq++;
                claimId = String.format("CLM-2026-%06d", claimSeq++);
                claimRemaps++;
            }
            usedClaimIds.add(claimId);
            try (PreparedStatement ps = conn.prepareStatement(clSql)) {
                ps.setString(1, claimId);
                ps.setLong(2, polId);
                ps.setLong(3, custId);
                ps.setString(4, a.type != null ? a.type : "General");
                ps.setBigDecimal(5, a.amount != null ? a.amount : BigDecimal.ZERO);
                ps.setString(6, a.status != null ? a.status : "OPEN");
                ps.setString(7, a.decision != null ? a.decision : "PENDING");
                ps.setString(8, a.agentId);
                ps.setTimestamp(9, Timestamp.valueOf(a.submittedAt != null ? a.submittedAt : LocalDateTime.now()));
                ps.executeUpdate();
            }
            claimCount++;
        }

        // ---------- payments ----------
        int paymentCount = 0;
        String paySql = "INSERT INTO payments (payment_id,transaction_id,policy_id,customer_id,amount,payment_method,status,gateway,failure_reason,paid_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)";
        for (PayAcc a : buildPayments(events)) {
            Long polId = polIdByCode.get(a.policyCode);
            Long custId = custIdByCode.get(a.custCode);
            if (polId == null || custId == null) continue;
            try (PreparedStatement ps = conn.prepareStatement(paySql)) {
                ps.setString(1, a.paymentId);
                ps.setString(2, a.transactionId);
                ps.setLong(3, polId);
                ps.setLong(4, custId);
                ps.setBigDecimal(5, a.amount != null ? a.amount : BigDecimal.ZERO);
                ps.setString(6, a.method != null ? a.method : "UNKNOWN");
                ps.setString(7, a.status != null ? a.status : "SUCCESS");
                ps.setString(8, a.gateway);
                ps.setString(9, a.failureReason);
                ps.setTimestamp(10, Timestamp.valueOf(a.paidAt != null ? a.paidAt : LocalDateTime.now()));
                ps.executeUpdate();
            }
            paymentCount++;
        }

        System.out.println("=================================================");
        System.out.println(" [LogLoader] Load complete:");
        System.out.println("   customers : " + custIdByCode.size() + " (correlation_id remaps: " + corrRemaps + ")");
        System.out.println("   policies  : " + policyCount);
        System.out.println("   claims    : " + claimCount + " (claim_id remaps: " + claimRemaps + ")");
        System.out.println("   payments  : " + paymentCount);
        System.out.println(" Imported customers log in with: cust-<code> / password123");
        System.out.println("=================================================");
    }

    // -------------------------------------------------------------- JDBC util

    private static boolean exists(Connection conn, String sql, String arg) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static Set<String> queryStrings(Connection conn, String sql) throws SQLException {
        Set<String> out = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) out.add(v);
            }
        }
        return out;
    }

    private static long returnedId(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ------------------------------------------------------------------ .env

    private static Map<String, String> loadEnv() {
        Map<String, String> map = new HashMap<>();
        Path env = Path.of(".env");
        if (Files.isRegularFile(env)) {
            try {
                for (String line : Files.readAllLines(env)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } catch (Exception ignore) {
                // fall through to env vars
            }
        }
        // environment variables win over .env
        for (String k : List.of("SUPABASE_DB_URL", "SUPABASE_DB_USER", "SUPABASE_DB_PASSWORD")) {
            String v = System.getenv(k);
            if (v != null && !v.isBlank()) map.put(k, v);
        }
        return map;
    }

    // ------------------------------------------------------------- log parse

    private static List<JsonNode> readEvents(Path file) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try { out.add(MAPPER.readTree(line)); } catch (Exception ignore) { /* skip bad line */ }
            }
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static LocalDateTime ts(JsonNode n) {
        String t = text(n, "timestamp");
        if (t == null) return null;
        try { return OffsetDateTime.parse(t).toLocalDateTime(); }
        catch (Exception e) {
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

    // --------------------------------------------------------- accumulators

    private static class CustAcc { String code; String segment; String corr; LocalDateTime created; }
    private static class PolAcc { String code; String type; String agentId; String custCode; boolean docGenerated; boolean anyFailed; LocalDateTime created; }
    private static class ClmAcc { String code; String policyCode; String custCode; String type; BigDecimal amount; String status; String decision; String agentId; LocalDateTime submittedAt; }
    private static class PayAcc { String paymentId; String transactionId; String policyCode; String custCode; BigDecimal amount; String method; String status; String gateway; String failureReason; LocalDateTime paidAt; }

    private static List<CustAcc> buildCustomers(List<JsonNode> events) {
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

    private static Map<String, BigDecimal> premiumsByPolicy(List<JsonNode> events) {
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

    private static List<PolAcc> buildPolicies(List<JsonNode> events) {
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

    private static List<ClmAcc> buildClaims(List<JsonNode> events) {
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
            evs.sort(Comparator.comparing(x -> { String t = text(x, "timestamp"); return t == null ? "" : t; }));
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

    private static List<PayAcc> buildPayments(List<JsonNode> events) {
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
