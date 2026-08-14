package com.example.ratelimiter.service;

import java.util.Optional;

/**
 * Redis adaptive multiplier state ({@code rl:v1:adapt:{identifier}:{namespace}}, TTL 120s).
 */
public interface AdaptiveStateStore {

    void put(String identifier, String namespace, AdaptiveState state);

    Optional<AdaptiveState> get(String identifier, String namespace);

    record AdaptiveState(double multiplier, double errorRate) {}
}
