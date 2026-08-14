package com.example.ratelimiter.service;

import java.util.Optional;

/** Stand-in when Redis is not available (Surefire unit profile). */
public class NoOpAdaptiveStateStore implements AdaptiveStateStore {

    @Override
    public void put(String identifier, String namespace, AdaptiveState state) {
        // no-op
    }

    @Override
    public Optional<AdaptiveState> get(String identifier, String namespace) {
        return Optional.empty();
    }
}
