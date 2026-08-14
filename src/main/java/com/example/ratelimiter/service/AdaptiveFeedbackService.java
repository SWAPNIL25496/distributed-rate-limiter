package com.example.ratelimiter.service;

import com.example.ratelimiter.controller.dto.AdaptiveFeedbackRequest;
import com.example.ratelimiter.controller.dto.AdaptiveFeedbackResponse;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdaptiveFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveFeedbackService.class);

    private final RateLimitRuleService ruleService;
    private final AdaptiveStateStore adaptiveStateStore;

    public AdaptiveFeedbackService(RateLimitRuleService ruleService, AdaptiveStateStore adaptiveStateStore) {
        this.ruleService = ruleService;
        this.adaptiveStateStore = adaptiveStateStore;
    }

    @Transactional(readOnly = true)
    public AdaptiveFeedbackResponse feedback(AdaptiveFeedbackRequest request) {
        String identifier = request.identifier().trim();
        String namespace = request.namespace().trim();
        double errorRate = request.downstreamErrorRate();

        RuleCache.CachedRule rule = ruleService
                .resolveCached(identifier, namespace)
                .map(RateLimitRuleService.ResolvedRule::rule)
                .filter(RuleCache.CachedRule::enabled)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        double multiplier = AdaptiveLimits.multiplierFor(errorRate);
        adaptiveStateStore.put(
                identifier, namespace, new AdaptiveStateStore.AdaptiveState(multiplier, errorRate));

        log.info(
                "Adaptive feedback identifier={} namespace={} errorRate={} multiplier={} adaptiveEnabled={}",
                identifier,
                namespace,
                errorRate,
                multiplier,
                rule.adaptiveEnabled());

        return new AdaptiveFeedbackResponse(
                identifier, namespace, errorRate, multiplier, AdaptiveLimits.TTL_SECONDS);
    }
}
