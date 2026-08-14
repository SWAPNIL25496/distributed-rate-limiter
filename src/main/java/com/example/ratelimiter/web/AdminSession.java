package com.example.ratelimiter.web;

/**
 * Session attribute holding the API key entered at {@code /drl/admin/login}.
 */
public final class AdminSession {

    public static final String API_KEY_ATTRIBUTE = "DRL_ADMIN_API_KEY";

    private AdminSession() {}
}
