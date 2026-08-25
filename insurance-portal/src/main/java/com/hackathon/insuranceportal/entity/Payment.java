package com.hackathon.insuranceportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A premium payment, made either by the customer themselves (self-service)
 * or recorded manually by an agent on the customer's behalf.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String paymentId;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private BigDecimal amount;

    /** CREDIT_CARD / NET_BANKING / WALLET / MANUAL (agent-recorded) */
    @Column(nullable = false)
    private String paymentMethod;

    /** SUCCESS / FAILED */
    @Column(nullable = false)
    private String status;

    /** Simulated payment gateway, e.g. "HDFC", "ICICI", "RAZORPAY" */
    private String gateway;

    /** Populated only when status = FAILED */
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime paidAt = LocalDateTime.now();
}
