package com.hackathon.insuranceportal.repository;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByPolicyId(String policyId);

    List<Policy> findByCustomer(Customer customer);

    List<Policy> findByCustomerOrderByStartDateDesc(Customer customer);

    /** Most recently created policy for a customer - used as the "primary"
     *  policy_id to attach to log lines that aren't about a specific policy. */
    Optional<Policy> findFirstByCustomerOrderByIdDesc(Customer customer);

    long countByStatus(String status);
}
