package com.hackathon.insuranceportal.config;

import com.hackathon.insuranceportal.util.IdGenerator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * At startup, wires {@link IdGenerator} to a single shared Postgres sequence
 * (app_id_seq) so business IDs are unique across ALL running app instances -
 * essential now that several people can run the app against the same shared
 * Supabase project at once. A database sequence hands out numbers atomically,
 * so two instances can never produce the same ID.
 *
 * The sequence is created once, seeded above the highest existing ID, and then
 * only ever advanced via nextval() - it is never reset, so restarts and
 * concurrent instances stay safe.
 *
 * On a non-Postgres database (the H2-backed unit tests) it does nothing and
 * IdGenerator falls back to its in-memory counter.
 */
@Component
@Order(1)
public class IdSequenceInitializer implements ApplicationRunner {

    private static final String SEQ = "app_id_seq";

    /** Highest numeric suffix already used across every business-id column. */
    private static final String MAX_SQL = """
            SELECT GREATEST(
              COALESCE((SELECT MAX(regexp_replace(correlation_id,'^.*[^0-9]','')::bigint) FROM customers WHERE correlation_id ~ '[0-9]$'),0),
              COALESCE((SELECT MAX(regexp_replace(claim_id,      '^.*[^0-9]','')::bigint) FROM claims    WHERE claim_id      ~ '[0-9]$'),0),
              COALESCE((SELECT MAX(regexp_replace(policy_id,     '^.*[^0-9]','')::bigint) FROM policies  WHERE policy_id     ~ '[0-9]$'),0),
              COALESCE((SELECT MAX(regexp_replace(payment_id,    '^.*[^0-9]','')::bigint) FROM payments  WHERE payment_id    ~ '[0-9]$'),0),
              COALESCE((SELECT MAX(regexp_replace(transaction_id,'^.*[^0-9]','')::bigint) FROM payments  WHERE transaction_id ~ '[0-9]$'),0)
            )
            """;

    private final JdbcTemplate jdbc;

    public IdSequenceInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product = jdbc.execute(
                    (ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
            if (product != null && product.toLowerCase().contains("postgre")) {
                Long max = jdbc.queryForObject(MAX_SQL, Long.class);
                long start = (max == null ? 0L : max) + 1;
                // Created once, seeded above the current max; a no-op if it already exists.
                jdbc.execute("CREATE SEQUENCE IF NOT EXISTS " + SEQ + " START WITH " + start + " INCREMENT BY 1");
                IdGenerator.enableSequence(jdbc, SEQ);
                System.out.println("[IdSequenceInitializer] IDs now come from shared sequence '" + SEQ
                        + "' - unique across all app instances.");
            } else {
                System.out.println("[IdSequenceInitializer] Non-Postgres database ("
                        + product + "); using in-memory ID counter.");
            }
        } catch (Exception e) {
            System.err.println("[IdSequenceInitializer] Could not set up the shared ID sequence ("
                    + e.getMessage() + "); falling back to the in-memory counter.");
        }
    }
}
