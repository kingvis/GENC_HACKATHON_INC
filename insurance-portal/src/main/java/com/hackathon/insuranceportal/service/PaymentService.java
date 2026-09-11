package com.hackathon.insuranceportal.service;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.entity.Payment;
import com.hackathon.insuranceportal.entity.Policy;
import com.hackathon.insuranceportal.exception.PaymentDeclinedException;
import com.hackathon.insuranceportal.logging.LogEvent;
import com.hackathon.insuranceportal.logging.LogWriter;
import com.hackathon.insuranceportal.repository.PaymentRepository;
import com.hackathon.insuranceportal.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processes premium payments. Since there's no real payment gateway in a
 * prototype, the "gateway" is simulated: a small percentage of attempts are
 * randomly declined, which is exactly what lets you demo the FAILED log
 * path (payment_method + gateway + failure_reason) without needing a real
 * bank integration.
 */
@Service
public class PaymentService {

    private static final String APPLICATION_NAME = "PaymentService";
    private static final String SERVER_ID = "payment-app-01";
    private static final String[] GATEWAYS = {"HDFC", "ICICI", "RAZORPAY"};

    /** Roughly 1 in 8 payments is declined, purely to demo the failure path. */
    private static final int FAILURE_RATE_PERCENT = 12;

    private final PaymentRepository paymentRepository;
    private final LogWriter logWriter;

    public PaymentService(PaymentRepository paymentRepository, LogWriter logWriter) {
        this.paymentRepository = paymentRepository;
        this.logWriter = logWriter;
    }

    @Transactional
    public Payment processPayment(Customer customer, Policy policy, BigDecimal amount,
                                   String paymentMethod, boolean isAgentRecorded) {
        long start = System.currentTimeMillis();
        String gateway = GATEWAYS[ThreadLocalRandom.current().nextInt(GATEWAYS.length)];
        boolean declined = !isAgentRecorded
                && ThreadLocalRandom.current().nextInt(100) < FAILURE_RATE_PERCENT;

        Payment payment = new Payment();
        payment.setPaymentId(IdGenerator.nextPaymentId());
        payment.setTransactionId(IdGenerator.nextTransactionId());
        payment.setPolicy(policy);
        payment.setCustomer(customer);
        payment.setAmount(amount);
        payment.setPaymentMethod(isAgentRecorded ? "MANUAL" : paymentMethod);
        payment.setGateway(gateway);

        if (declined) {
            payment.setStatus("FAILED");
            payment.setFailureReason("Transaction Error");
            Payment saved = paymentRepository.save(payment);

            logWriter.write(new LogEvent()
                    .applicationName(APPLICATION_NAME)
                    .serverId(SERVER_ID)
                    .customerId(customer.getId())
                    .policyId(policy.getPolicyId())
                    .claimId(null)
                    .eventType("Payment Failed")
                    .stage("PREMIUM_COLLECTION")
                    .status("WARNING")
                    .durationMs(System.currentTimeMillis() - start)
                    .error("Transaction Error")
                    .extra("payment_id", saved.getPaymentId())
                    .extra("transaction_id", saved.getTransactionId())
                    .extra("amount", amount)
                    .extra("payment_method", saved.getPaymentMethod())
                    .extra("policy_type", policy.getPolicyType())
                    .extra("gateway", gateway));

            throw new PaymentDeclinedException("PAYMENT_DECLINED",
                    "Payment was declined by the gateway");
        }

        payment.setStatus("SUCCESS");
        Payment saved = paymentRepository.save(payment);

        logWriter.write(new LogEvent()
                .applicationName(APPLICATION_NAME)
                .serverId(SERVER_ID)
                .customerId(customer.getId())
                .policyId(policy.getPolicyId())
                .claimId(null)
                .eventType(isAgentRecorded ? "Payment Recorded" : "Payment Successful")
                .stage("PREMIUM_COLLECTION")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - start)
                .error(null)
                .extra("payment_id", saved.getPaymentId())
                .extra("transaction_id", saved.getTransactionId())
                .extra("amount", amount)
                .extra("payment_method", saved.getPaymentMethod())
                .extra("policy_type", policy.getPolicyType())
                .extra("gateway", gateway));

        return saved;
    }

    public List<Payment> paymentsForCustomer(Customer customer) {
        return paymentRepository.findByCustomerOrderByPaidAtDesc(customer);
    }

    public List<Payment> allPayments() {
        return paymentRepository.findAll();
    }
}
