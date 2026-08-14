package com.example.ratelimiter.security;

import com.example.ratelimiter.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

/**
 * Rejects requests to protected paths that do not carry a valid {@code X-API-Key} header.
 *
 * <p>Protected and public path patterns are supplied by configuration so later phases can
 * extend coverage (for example the admin UI) without changing this filter.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String UNAUTHORIZED_MESSAGE = "Missing or invalid " + API_KEY_HEADER + " header";

    private final byte[] expectedApiKey;
    private final List<PathPattern> protectedPatterns;
    private final List<PathPattern> publicPatterns;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(String expectedApiKey,
                            List<String> protectedPatterns,
                            List<String> publicPatterns,
                            ObjectMapper objectMapper) {
        this.expectedApiKey = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        this.protectedPatterns = parse(protectedPatterns);
        this.publicPatterns = parse(publicPatterns);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        PathContainer path = PathContainer.parsePath(pathWithinApplication(request));
        return matches(publicPatterns, path) || !matches(protectedPatterns, path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isAuthorized(request.getHeader(API_KEY_HEADER))) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Rejected unauthorized request method={} path={}", request.getMethod(), request.getRequestURI());
        writeUnauthorized(request, response);
    }

    private boolean isAuthorized(String providedApiKey) {
        return providedApiKey != null
                && MessageDigest.isEqual(expectedApiKey, providedApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
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
