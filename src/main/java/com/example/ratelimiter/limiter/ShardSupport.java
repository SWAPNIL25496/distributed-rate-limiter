package com.example.ratelimiter.limiter;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Counter-shard selection and per-shard budget ([ADR 0010](docs/adr/0010-shard-ready-redis-keys.md)).
 * v1 defaults to {@code N=1} (only shard {@code 0}).
 */
public final class ShardSupport {

    private ShardSupport() {}

    public static int selectShardId(int counterShards) {
        if (counterShards < 1) {
            throw new IllegalArgumentException("counterShards must be >= 1");
        }
        if (counterShards == 1) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(counterShards);
    }

    /**
     * Per-shard effective limit so Σ admits across shards ≤ {@code effectiveLimit}.
     * Remainder {@code effectiveLimit % N} is assigned to shards {@code 0 .. rem-1}.
     */
    public static int shardLimit(int effectiveLimit, int shardId, int counterShards) {
        if (effectiveLimit < 1) {
            throw new IllegalArgumentException("effectiveLimit must be >= 1");
        }
        if (counterShards < 1) {
            throw new IllegalArgumentException("counterShards must be >= 1");
        }
        if (shardId < 0 || shardId >= counterShards) {
            throw new IllegalArgumentException("shardId out of range");
        }
        int base = effectiveLimit / counterShards;
        int rem = effectiveLimit % counterShards;
        return shardId < rem ? base + 1 : base;
    }
}
