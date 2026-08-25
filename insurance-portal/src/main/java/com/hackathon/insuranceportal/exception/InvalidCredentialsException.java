package com.hackathon.insuranceportal.exception;

/** Thrown when a login attempt fails, carrying a specific reason code
 *  (INVALID_CREDENTIALS, ACCOUNT_LOCKED, etc.) for the failure log. */
public class InvalidCredentialsException extends RuntimeException {

    private final String reasonCode;

    public InvalidCredentialsException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
