package com.example.ratelimiter.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Surefire-safe check that production Lua scripts are on the classpath and loadable. Full
 * Java↔Lua allow/deny parity runs under Failsafe {@code EvaluateIT} (requires Redis / Docker).
 */
class LuaScriptLoadingTest {

    @Test
    void tokenBucketScriptLoadsFromClasspath() {
        DefaultRedisScript<?> script = load("lua/token_bucket_evaluate.lua");
        assertThat(script.getScriptAsString()).contains("KEYS[1]");
        assertThat(script.getScriptAsString()).contains("tokens");
        assertThat(script.getScriptAsString()).contains("last_refill_ms");
    }

    @Test
    void slidingWindowScriptLoadsFromClasspath() {
        DefaultRedisScript<?> script = load("lua/sliding_window_evaluate.lua");
        assertThat(script.getScriptAsString()).contains("KEYS[1]");
        assertThat(script.getScriptAsString()).contains("window_start");
        assertThat(script.getScriptAsString()).contains("previous");
        assertThat(script.getScriptAsString()).contains("current");
    }

    private static DefaultRedisScript<?> load(String path) {
        DefaultRedisScript<Object> script = new DefaultRedisScript<>();
        script.setResultType(Object.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        assertThat(new ClassPathResource(path).exists()).isTrue();
        assertThat(script.getScriptAsString()).isNotBlank();
        return script;
    }
}
