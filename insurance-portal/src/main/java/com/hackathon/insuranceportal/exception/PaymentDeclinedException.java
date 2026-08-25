package com.hackathon.insuranceportal.exception;

/** Thrown when the simulated payment gateway declines a transaction. */
public class PaymentDeclinedException extends RuntimeException {

    private final String reasonCode;

    public PaymentDeclinedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
