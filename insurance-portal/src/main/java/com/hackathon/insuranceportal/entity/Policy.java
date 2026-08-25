package com.hackathon.insuranceportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An insurance policy belonging to a customer.
 * policyId is the human-readable business key used across all log lines
 * (e.g. "POL-2026-100101") - separate from the internal database id.
 */
@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String policyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Agent who created/manages this policy. Nullable if self-purchased. */
    private String agentId;

    /** AUTO / HOME / HEALTH / LIFE / TRAVEL */
    @Column(nullable = false)
    private String policyType;

    @Column(nullable = false)
    private BigDecimal premiumAmount;

    /** PENDING_APPROVAL / ACTIVE / RENEWED / EXPIRED / REJECTED */
    @Column(nullable = false)
    private String status = "PENDING_APPROVAL";

    @Column(nullable = false)
    private LocalDate startDate = LocalDate.now();

    @Column(nullable = false)
    private LocalDate renewalDate = LocalDate.now().plusYears(1);
}
