package com.hackathon.insuranceportal.exception;

/** Thrown when a requested policy_id does not exist. */
public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String message) {
        super(message);
    }
}
