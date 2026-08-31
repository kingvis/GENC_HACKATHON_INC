package com.hackathon.insuranceportal.config;

import com.hackathon.insuranceportal.util.IdGenerator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * At startup, advances {@link IdGenerator}'s counter past the highest business
 * id already stored in the database.
 *
 * Why this is needed: IdGenerator's counter lives in memory and resets on every
 * restart. Without this, after a restart (or after importing historical data)
 * it would regenerate ids like CORR-2026-000101 / CLM-2026-000101 that already
 * exist, causing "duplicate key" errors the first time a user creates a policy,
 * claim, or payment. Seeding it above the current maximum guarantees new ids
 * never collide with existing rows.
 *
 * The query extracts the trailing digit-run of each id (the part the counter
 * produces) and takes the overall maximum across the tables.
 */
@Component
public class IdSequenceInitializer implements ApplicationRunner {

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
            Long max = jdbc.queryForObject(MAX_SQL, Long.class);
            long high = (max == null ? 0L : max);
            IdGenerator.seedAtLeast(high + 1);
            System.out.println("[IdSequenceInitializer] Seeded IdGenerator above existing max "
                    + high + " - new ids start at " + (high + 1) + ".");
        } catch (Exception e) {
            System.err.println("[IdSequenceInitializer] Could not seed IdGenerator from the database: "
                    + e.getMessage() + " - continuing with the default counter.");
        }
    }
}
