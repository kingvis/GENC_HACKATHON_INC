package com.hackathon.insuranceportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A claim filed against a policy. claimId is the human-readable business
 * key used in log lines (e.g. "CLM-2026-000101").
 */
@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** e.g. "Vehicle Damage", "Hospitalization", "Fire Damage" */
    @Column(nullable = false)
    private String claimType;

    @Column(nullable = false)
    private BigDecimal claimAmount;

    /** OPEN / ASSIGNED / UNDER_REVIEW / APPROVED / REJECTED / SETTLED */
    @Column(nullable = false)
    private String claimStatus = "OPEN";

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false)
    private String claimDecision = "PENDING";

    private String agentId;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    /**
     * Last time this claim changed. Set on insert and refreshed automatically
     * on every update (e.g. each status/decision transition) by the JPA
     * lifecycle callback below - callers never set it by hand.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
