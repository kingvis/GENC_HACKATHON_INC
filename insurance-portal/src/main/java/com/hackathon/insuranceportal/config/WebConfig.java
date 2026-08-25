package com.hackathon.insuranceportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthInterceptor("customerId", "/customer/login"))
                .addPathPatterns("/customer/**")
                .excludePathPatterns("/customer/login", "/customer/register");

        registry.addInterceptor(new SessionAuthInterceptor("agentId", "/agent/login"))
                .addPathPatterns("/agent/**")
                .excludePathPatterns("/agent/login");
    }
}
