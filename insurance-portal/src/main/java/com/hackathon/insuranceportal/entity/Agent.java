package com.hackathon.insuranceportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An agent who logs in with an email/password to look up customers, create
 * and approve policies, process renewals, review/approve/reject/settle
 * claims, record manual premium payments, and pull reports.
 */
@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agents sign in with email, per the team's design decision. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    /** Human-readable agent code used in logs, e.g. "agent-411". */
    @Column(nullable = false, unique = true)
    private String agentCode;
}
