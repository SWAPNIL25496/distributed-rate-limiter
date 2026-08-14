package com.example.ratelimiter.controller;

import com.example.ratelimiter.controller.dto.EvaluateRequest;
import com.example.ratelimiter.controller.dto.EvaluateResponse;
import com.example.ratelimiter.service.EvaluateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluate")
@Tag(name = "Evaluate", description = "Atomic quota consume via Redis Lua")
public class EvaluateController {

    private final EvaluateService evaluateService;

    public EvaluateController(EvaluateService evaluateService) {
        this.evaluateService = evaluateService;
    }

    @PostMapping
    @Operation(summary = "Evaluate (consume one) against the matching enabled rule")
    @ApiResponse(responseCode = "200", description = "Allow or deny with remaining quota")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "No matching enabled rule")
    public EvaluateResponse evaluate(@Valid @RequestBody EvaluateRequest request) {
        return evaluateService.evaluate(request);
    }
}
