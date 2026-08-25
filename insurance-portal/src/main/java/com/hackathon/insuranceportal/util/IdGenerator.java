package com.hackathon.insuranceportal.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates human-readable business identifiers matching the format used
 * throughout the reference logs, e.g. POL-2026-100101, CLM-2026-000101,
 * CORR-2026-000101. These are separate from the internal database IDs -
 * they're what actually shows up in every log line.
 */
public final class IdGenerator {

    private static final int YEAR = 2026;
    private static final AtomicLong sequence = new AtomicLong(100);

    private IdGenerator() {
    }

    public static String nextCorrelationId() {
        return "CORR-" + YEAR + "-" + pad(sequence.incrementAndGet());
    }

    public static String nextPolicyId() {
        return "POL-" + YEAR + "-1" + pad(sequence.incrementAndGet());
    }

    public static String nextClaimId() {
        return "CLM-" + YEAR + "-" + pad(sequence.incrementAndGet());
    }

    public static String nextPaymentId() {
        return "PAY-" + pad(sequence.incrementAndGet());
    }

    public static String nextTransactionId() {
        return "TXN-" + (1_000_000 + ThreadLocalRandom.current().nextInt(900_000));
    }

    public static String nextProposalId() {
        return "PROP-" + YEAR + "-1" + pad(sequence.incrementAndGet());
    }

    private static String pad(long value) {
        return String.format("%06d", value);
    }
}
