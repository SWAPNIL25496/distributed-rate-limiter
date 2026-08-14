package com.example.ratelimiter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Protects {@code /drl/admin/**} with an HttpSession API key (except login + static assets).
 */
public class AdminSessionAuthFilter extends OncePerRequestFilter {

    private final byte[] expectedApiKey;
    private final List<PathPattern> protectedPatterns;
    private final List<PathPattern> publicPatterns;

    public AdminSessionAuthFilter(
            String expectedApiKey, List<String> protectedPatterns, List<String> publicPatterns) {
        this.expectedApiKey = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        this.protectedPatterns = parse(protectedPatterns);
        this.publicPatterns = parse(publicPatterns);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(pathWithinApplication(request));
        return matches(publicPatterns, path) || !matches(protectedPatterns, path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object stored = session == null ? null : session.getAttribute(AdminSession.API_KEY_ATTRIBUTE);
        if (stored instanceof String key && isAuthorized(key)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/drl/admin/login");
    }

    private boolean isAuthorized(String providedApiKey) {
        return MessageDigest.isEqual(expectedApiKey, providedApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private static List<PathPattern> parse(List<String> patterns) {
        return patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    private static boolean matches(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
