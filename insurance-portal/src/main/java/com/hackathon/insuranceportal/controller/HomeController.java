package com.hackathon.insuranceportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page - "START -> Landing Page" from the flowchart. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String landingPage() {
        return "index";
    }
}
