package com.example.ratelimiter.limiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CounterKeysTest {

    @Test
    void buildsShardReadyTokenBucketAndSlidingWindowKeys() {
        assertThat(CounterKeys.tokenBucket("tenant-42", "checkout", 0))
                .isEqualTo("rl:v1:tb:tenant-42:checkout:0");
        assertThat(CounterKeys.slidingWindow("tenant-42", "checkout", 0))
                .isEqualTo("rl:v1:sw:tenant-42:checkout:0");
        assertThat(CounterKeys.adaptive("tenant-42", "checkout"))
                .isEqualTo("rl:v1:adapt:tenant-42:checkout");
    }
}

class ShardSupportTest {

    @Test
    void singleShardAlwaysUsesZeroAndFullLimit() {
        assertThat(ShardSupport.selectShardId(1)).isZero();
        assertThat(ShardSupport.shardLimit(100, 0, 1)).isEqualTo(100);
    }

    @Test
    void distributesRemainderAcrossLowShardIds() {
        // 10 / 3 → base 3, rem 1 → shards 0=4, 1=3, 2=3
        assertThat(ShardSupport.shardLimit(10, 0, 3)).isEqualTo(4);
        assertThat(ShardSupport.shardLimit(10, 1, 3)).isEqualTo(3);
        assertThat(ShardSupport.shardLimit(10, 2, 3)).isEqualTo(3);
        assertThat(4 + 3 + 3).isEqualTo(10);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> ShardSupport.selectShardId(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShardSupport.shardLimit(5, 5, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
