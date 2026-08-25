package com.hackathon.insuranceportal.repository;

import com.hackathon.insuranceportal.entity.Claim;
import com.hackathon.insuranceportal.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByClaimId(String claimId);

    List<Claim> findByCustomer(Customer customer);

    List<Claim> findByCustomerOrderBySubmittedAtDesc(Customer customer);

    Optional<Claim> findFirstByCustomerOrderByIdDesc(Customer customer);

    List<Claim> findByClaimStatus(String claimStatus);

    long countByClaimStatus(String claimStatus);
}
