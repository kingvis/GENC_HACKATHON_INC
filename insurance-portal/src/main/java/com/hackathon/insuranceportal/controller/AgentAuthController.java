package com.hackathon.insuranceportal.controller;

import com.hackathon.insuranceportal.entity.Agent;
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

/** Handles the "Agent Login" box from the flowchart. */
@Controller
@RequestMapping("/agent")
public class AgentAuthController {

    private final AuthService authService;

    public AgentAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "agent-login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                         @RequestParam String password,
                         HttpServletRequest request,
                         Model model) {
        try {
            Agent agent = authService.loginAgent(email, password);
            HttpSession session = request.getSession(true);
            session.setAttribute("agentId", agent.getAgentCode());
            session.setAttribute("agentName", agent.getFullName());
            return "redirect:/agent/home";
        } catch (InvalidCredentialsException e) {
            model.addAttribute("error", "Invalid email or password.");
            return "agent-login";
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
