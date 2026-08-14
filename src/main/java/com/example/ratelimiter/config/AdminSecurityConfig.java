package com.example.ratelimiter.config;

import com.example.ratelimiter.web.AdminSessionAuthFilter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AdminSecurityConfig {

    static final List<String> PROTECTED_PATTERNS = List.of("/drl/admin", "/drl/admin/**");

    static final List<String> PUBLIC_PATTERNS = List.of(
            "/drl/admin/login",
            "/drl/admin/css/**");

    @Bean
    FilterRegistrationBean<AdminSessionAuthFilter> adminSessionAuthFilter(AppProperties appProperties) {
        AdminSessionAuthFilter filter = new AdminSessionAuthFilter(
                appProperties.apiKey(), PROTECTED_PATTERNS, PUBLIC_PATTERNS);
        FilterRegistrationBean<AdminSessionAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
