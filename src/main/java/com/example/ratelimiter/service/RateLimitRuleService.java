package com.example.ratelimiter.service;

import com.example.ratelimiter.controller.dto.RuleRequest;
import com.example.ratelimiter.controller.dto.RuleResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.domain.RateLimitRule;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.exception.ConflictException;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.repository.RateLimitRuleRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitRuleService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRuleService.class);

    private final RateLimitRuleRepository repository;
    private final RuleCache ruleCache;

    public RateLimitRuleService(RateLimitRuleRepository repository, RuleCache ruleCache) {
        this.repository = repository;
        this.ruleCache = ruleCache;
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        validateAlgorithmFields(request);
        if (repository.existsByIdentifierAndNamespace(request.identifier(), request.namespace())) {
            throw new ConflictException("Rule already exists for identifier and namespace");
        }

        RateLimitRule rule = new RateLimitRule();
        apply(rule, request);
        RateLimitRule saved = repository.save(rule);
        writeThroughCache(saved);

        log.info(
                "Created rule id={} identifier={} namespace={} algorithm={}",
                saved.getId(),
                saved.getIdentifier(),
                saved.getNamespace(),
                saved.getAlgorithm());
        return RuleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(int limit) {
        return repository.findAll(PageRequest.of(0, limit)).stream().map(RuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(Long id) {
        return RuleResponse.from(requireRule(id));
    }

    @Transactional
    public RuleResponse update(Long id, RuleRequest request) {
        validateAlgorithmFields(request);
        RateLimitRule rule = requireRule(id);

        String previousIdentifier = rule.getIdentifier();
        String previousNamespace = rule.getNamespace();

        if (repository.existsByIdentifierAndNamespaceAndIdNot(
                request.identifier(), request.namespace(), id)) {
            throw new ConflictException("Rule already exists for identifier and namespace");
        }

        apply(rule, request);
        RateLimitRule saved = repository.save(rule);

        if (!previousIdentifier.equals(saved.getIdentifier())
                || !previousNamespace.equals(saved.getNamespace())) {
            ruleCache.evict(previousIdentifier, previousNamespace);
        }
        writeThroughCache(saved);

        log.info(
                "Updated rule id={} identifier={} namespace={} enabled={}",
                saved.getId(),
                saved.getIdentifier(),
                saved.getNamespace(),
                saved.isEnabled());
        return RuleResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        RateLimitRule rule = requireRule(id);
        repository.delete(rule);
        ruleCache.evict(rule.getIdentifier(), rule.getNamespace());
        log.info(
                "Deleted rule id={} identifier={} namespace={}",
                id,
                rule.getIdentifier(),
                rule.getNamespace());
    }

    /**
     * Cache-aside helper for later evaluate/observe phases: Redis first, Postgres on miss.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedRule> resolveCached(String identifier, String namespace) {
        Optional<RuleCache.CachedRule> cached = ruleCache.get(identifier, namespace);
        if (cached.isPresent()) {
            return Optional.of(new ResolvedRule(cached.get(), true));
        }
        return repository
                .findByIdentifierAndNamespace(identifier, namespace)
                .map(rule -> {
                    ruleCache.put(rule);
                    return new ResolvedRule(RuleCache.CachedRule.from(rule), false);
                });
    }

    public record ResolvedRule(RuleCache.CachedRule rule, boolean cacheHit) {
    }

    private RateLimitRule requireRule(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + id));
    }

    private void writeThroughCache(RateLimitRule rule) {
        if (rule.isEnabled()) {
            ruleCache.put(rule);
        } else {
            ruleCache.evict(rule.getIdentifier(), rule.getNamespace());
        }
    }

    private void apply(RateLimitRule rule, RuleRequest request) {
        rule.setIdentifier(request.identifier().trim());
        rule.setNamespace(request.namespace().trim());
        rule.setAlgorithm(request.algorithm());
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setAdaptiveEnabled(request.adaptiveEnabled() == null || request.adaptiveEnabled());

        if (request.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            rule.setBurstCapacity(request.burstCapacity());
            rule.setRefillPerSecond(request.refillPerSecond());
            rule.setLimitCount(null);
            rule.setWindowSeconds(null);
        } else {
            rule.setBurstCapacity(null);
            rule.setRefillPerSecond(null);
            rule.setLimitCount(request.limit());
            rule.setWindowSeconds(request.windowSeconds());
        }
    }

    static void validateAlgorithmFields(RuleRequest request) {
        if (request.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            if (request.burstCapacity() == null || request.burstCapacity() < 1) {
                throw new BadRequestException("TOKEN_BUCKET requires burstCapacity >= 1");
            }
            if (request.refillPerSecond() == null || request.refillPerSecond() <= 0) {
                throw new BadRequestException("TOKEN_BUCKET requires refillPerSecond > 0");
            }
            return;
        }
        if (request.algorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
            if (request.limit() == null || request.limit() < 1) {
                throw new BadRequestException("SLIDING_WINDOW requires limit >= 1");
            }
            if (request.windowSeconds() == null || request.windowSeconds() < 1) {
                throw new BadRequestException("SLIDING_WINDOW requires windowSeconds >= 1");
            }
            return;
        }
        throw new BadRequestException("Unsupported algorithm");
    }
}
