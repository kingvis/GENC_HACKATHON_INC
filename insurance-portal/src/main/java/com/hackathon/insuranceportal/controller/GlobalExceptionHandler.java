package com.hackathon.insuranceportal.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Catches anything not already handled inside a controller (e.g. a
 * customer session pointing at a deleted record) and shows a plain error
 * page instead of a raw stack trace. Controllers handle their own expected
 * failures (invalid login, declined payment, etc.) directly - this is
 * strictly the safety net for anything unexpected.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleAnyException(Exception ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error";
    }
}
