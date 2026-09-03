package com.hackathon.insuranceportal.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates human-readable business identifiers matching the format used
 * throughout the reference logs, e.g. POL-2026-100101, CLM-2026-000101,
 * CORR-2026-000101. These are separate from the internal database IDs -
 * they're what actually shows up in every log line.
 *
 * The numeric part comes from a single shared Postgres sequence (app_id_seq)
 * when the app is wired up at startup (see IdSequenceInitializer). Because the
 * sequence lives in the database, every app instance - even several running at
 * once against the same shared Supabase project - draws unique, non-colliding
 * numbers. If no sequence is active (e.g. the H2-backed unit tests), it falls
 * back to an in-memory counter.
 */
public final class IdGenerator {

    private static final int YEAR = 2026;

    /** In-memory fallback, used only when no DB sequence is wired up (tests). */
    private static final AtomicLong fallback = new AtomicLong(100);

    /** When set, IDs are drawn from the shared DB sequence instead. */
    private static volatile JdbcTemplate seqJdbc;
    private static volatile String seqName = "app_id_seq";

    private IdGenerator() {
    }

    /** Wire IDs to a shared database sequence (atomic across all instances). */
    public static void enableSequence(JdbcTemplate jdbc, String sequenceName) {
        seqName = sequenceName;
        seqJdbc = jdbc;
    }

    /** In-memory fallback seeding - only affects the non-sequence path. */
    public static void seedAtLeast(long value) {
        fallback.updateAndGet(current -> Math.max(current, value));
    }

    private static long next() {
        JdbcTemplate jdbc = seqJdbc;
        if (jdbc != null) {
            Long value = jdbc.queryForObject("select nextval('" + seqName + "')", Long.class);
            if (value != null) {
                return value;
            }
        }
        return fallback.incrementAndGet();
    }

    public static String nextCorrelationId() {
        return "CORR-" + YEAR + "-" + pad(next());
    }

    public static String nextPolicyId() {
        return "POL-" + YEAR + "-1" + pad(next());
    }

    public static String nextClaimId() {
        return "CLM-" + YEAR + "-" + pad(next());
    }

    public static String nextPaymentId() {
        return "PAY-" + pad(next());
    }

    public static String nextTransactionId() {
        return "TXN-" + pad(next());
    }

    public static String nextProposalId() {
        return "PROP-" + YEAR + "-1" + pad(next());
    }

    private static String pad(long value) {
        return String.format("%06d", value);
    }
}
