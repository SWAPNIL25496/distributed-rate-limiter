package com.example.ratelimiter.repository;

import com.example.ratelimiter.domain.RateLimitRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, Long> {

    boolean existsByIdentifierAndNamespace(String identifier, String namespace);

    boolean existsByIdentifierAndNamespaceAndIdNot(String identifier, String namespace, Long id);

    Optional<RateLimitRule> findByIdentifierAndNamespace(String identifier, String namespace);
}
