package com.hackathon.insuranceportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A customer who logs into the portal to view policies, track claims, pay
 * premiums, and download documents.
 *
 * correlationId is intentionally NOT generated at signup. It is generated
 * once, the first time a policy is created for this customer, and then
 * reused for every future log line this customer generates - across every
 * feature, forever. This mirrors the real reference logs: correlation_id
 * traces a customer's entire relationship with the company, not a single
 * login session. See LogWriter for how it is looked up.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    /**
     * Generated once at first policy creation. Null until then.
     * Format: CORR-2026-000101
     */
    @Column(unique = true)
    private String correlationId;

    /**
     * STANDARD / PREMIUM / VIP - used purely as a log dimension for the
     * monitoring app (e.g. "are VIP customers hitting more failures?").
     */
    @Column(nullable = false)
    private String customerSegment = "STANDARD";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
