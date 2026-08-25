package com.hackathon.insuranceportal.controller;

import com.hackathon.insuranceportal.entity.Customer;
import com.hackathon.insuranceportal.exception.InvalidCredentialsException;
import com.hackathon.insuranceportal.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Handles the "Customer Login" and self-registration boxes from the flowchart. */
@Controller
@RequestMapping("/customer")
public class CustomerAuthController {

    private final AuthService authService;

    public CustomerAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "customer-login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                         @RequestParam String password,
                         HttpServletRequest request,
                         Model model) {
        HttpSession session = request.getSession(true);
        String sessionId = authService.newSessionId();
        String ipAddress = request.getRemoteAddr();

        try {
            Customer customer = authService.loginCustomer(username, password, sessionId, ipAddress, "WEB");
            session.setAttribute("customerId", customer.getId());
            session.setAttribute("customerName", customer.getFullName());
            session.setAttribute("sessionLogId", sessionId);
            return "redirect:/customer/home";
        } catch (InvalidCredentialsException e) {
            model.addAttribute("error", "Invalid username or password.");
            return "customer-login";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "customer-register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                            @RequestParam String password,
                            @RequestParam String fullName,
                            Model model) {
        try {
            authService.register(username, password, fullName);
            return "redirect:/customer/login?registered=true";
        } catch (InvalidCredentialsException e) {
            model.addAttribute("error", e.getMessage());
            return "customer-register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }
}
