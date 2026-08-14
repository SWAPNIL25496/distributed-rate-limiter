package com.example.ratelimiter.limiter;

/**
 * Shard-ready Redis counter key helpers ([ADR 0010](docs/adr/0010-shard-ready-redis-keys.md)).
 */
public final class CounterKeys {

    public static final String TOKEN_BUCKET_PREFIX = "rl:v1:tb:";
    public static final String SLIDING_WINDOW_PREFIX = "rl:v1:sw:";

    private CounterKeys() {}

    public static String tokenBucket(String identifier, String namespace, int shardId) {
        return TOKEN_BUCKET_PREFIX + identifier + ":" + namespace + ":" + shardId;
    }

    public static String slidingWindow(String identifier, String namespace, int shardId) {
        return SLIDING_WINDOW_PREFIX + identifier + ":" + namespace + ":" + shardId;
    }
}
