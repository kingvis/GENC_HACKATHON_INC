package com.hackathon.insuranceportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards every /customer/** and /agent/** route (except the login pages
 * themselves) behind a simple session check. This is intentionally basic
 * (no Spring Security, no JWT) - the right call for a hackathon prototype
 * where the goal is a working demo, not production-grade auth hardening.
 */
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final String sessionAttribute;
    private final String loginRedirect;

    public SessionAuthInterceptor(String sessionAttribute, String loginRedirect) {
        this.sessionAttribute = sessionAttribute;
        this.loginRedirect = loginRedirect;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null && session.getAttribute(sessionAttribute) != null;

        if (!authenticated) {
            response.sendRedirect(loginRedirect);
            return false;
        }
        return true;
    }
}
