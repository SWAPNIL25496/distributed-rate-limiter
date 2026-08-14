package com.example.ratelimiter.web;

import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.controller.dto.RuleResponse;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.service.QuotaService;
import com.example.ratelimiter.service.RateLimitRuleService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/drl/admin")
public class AdminController {

    private final RateLimitRuleService ruleService;
    private final QuotaService quotaService;

    public AdminController(RateLimitRuleService ruleService, QuotaService quotaService) {
        this.ruleService = ruleService;
        this.quotaService = quotaService;
    }

    @GetMapping
    public String rules(Model model) {
        List<RuleResponse> rules = ruleService.list(200);
        model.addAttribute("rules", rules);
        return "admin/rules";
    }

    @GetMapping("/quotas")
    public String quotas(
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "namespace", required = false) String namespace,
            Model model) {
        List<RuleResponse> rules = ruleService.list(200);
        model.addAttribute("rules", rules);
        model.addAttribute("selectedIdentifier", identifier);
        model.addAttribute("selectedNamespace", namespace);

        List<QuotaRow> rows = new ArrayList<>();
        if (identifier != null && !identifier.isBlank() && namespace != null && !namespace.isBlank()) {
            rows.add(observeRow(identifier.trim(), namespace.trim()));
        } else {
            for (RuleResponse rule : rules) {
                if (rule.enabled()) {
                    rows.add(observeRow(rule.identifier(), rule.namespace()));
                }
            }
        }
        model.addAttribute("quotas", rows);
        return "admin/quotas";
    }

    @GetMapping(value = "/quotas/live", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<QuotaRow> quotasLive(
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "namespace", required = false) String namespace) {
        List<QuotaRow> rows = new ArrayList<>();
        if (identifier != null && !identifier.isBlank() && namespace != null && !namespace.isBlank()) {
            rows.add(observeRow(identifier.trim(), namespace.trim()));
            return rows;
        }
        for (RuleResponse rule : ruleService.list(200)) {
            if (rule.enabled()) {
                rows.add(observeRow(rule.identifier(), rule.namespace()));
            }
        }
        return rows;
    }

    private QuotaRow observeRow(String identifier, String namespace) {
        try {
            QuotaResponse q = quotaService.observe(identifier, namespace);
            return QuotaRow.from(q, null);
        } catch (ResourceNotFoundException ex) {
            return new QuotaRow(identifier, namespace, null, null, null, null, null, null, null, "no enabled rule");
        } catch (RuntimeException ex) {
            return new QuotaRow(
                    identifier, namespace, null, null, null, null, null, null, null, ex.getMessage());
        }
    }

    public record QuotaRow(
            String identifier,
            String namespace,
            String algorithm,
            Integer consumed,
            Integer remaining,
            Integer limit,
            Integer effectiveLimit,
            Double adaptiveMultiplier,
            Double downstreamErrorRate,
            String error) {

        static QuotaRow from(QuotaResponse q, String error) {
            return new QuotaRow(
                    q.identifier(),
                    q.namespace(),
                    q.algorithm() == null ? null : q.algorithm().name(),
                    q.consumed(),
                    q.remaining(),
                    q.limit(),
                    q.effectiveLimit(),
                    q.adaptiveMultiplier(),
                    q.downstreamErrorRate(),
                    error);
        }
    }
}
