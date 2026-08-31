package com.hackathon.insuranceportal.repository;

import com.hackathon.insuranceportal.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByAgentCode(String agentCode);
}
