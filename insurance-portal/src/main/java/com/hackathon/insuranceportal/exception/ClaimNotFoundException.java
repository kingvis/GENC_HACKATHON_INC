package com.hackathon.insuranceportal.exception;

/** Thrown when a requested claim_id does not exist. */
public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(String message) {
        super(message);
    }
}
