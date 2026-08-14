package com.example.ratelimiter.controller;

import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotas")
@Tag(name = "Quotas", description = "Read-only quota observe (no consume)")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/{identifier}")
    @Operation(summary = "Observe consumption / remaining / reset without consuming quota")
    @ApiResponse(responseCode = "200", description = "Quota snapshot")
    @ApiResponse(responseCode = "400", description = "Missing namespace")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "No matching enabled rule")
    public QuotaResponse observe(
            @PathVariable String identifier,
            @RequestParam(required = false) String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new BadRequestException("namespace is required");
        }
        return quotaService.observe(identifier, namespace);
    }
}
