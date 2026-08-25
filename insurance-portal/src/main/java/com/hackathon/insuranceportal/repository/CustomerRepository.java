package com.hackathon.insuranceportal.repository;

import com.hackathon.insuranceportal.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Customer> findByFullNameContainingIgnoreCaseOrUsernameContainingIgnoreCase(
            String fullName, String username);
}
