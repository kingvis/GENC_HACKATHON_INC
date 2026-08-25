package com.hackathon.insuranceportal.repository;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByCustomerOrderByPaidAtDesc(Customer customer);

    long countByStatus(String status);
}
