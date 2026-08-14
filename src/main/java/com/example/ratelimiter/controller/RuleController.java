package com.example.ratelimiter.controller;

import com.example.ratelimiter.controller.dto.RuleRequest;
import com.example.ratelimiter.controller.dto.RuleResponse;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.service.RateLimitRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
@Tag(name = "Rules", description = "Rate limit rule CRUD (Postgres SoR + Redis write-through cache)")
public class RuleController {

    static final int DEFAULT_LIST_LIMIT = 50;
    static final int MAX_LIST_LIMIT = 200;

    private final RateLimitRuleService ruleService;

    public RuleController(RateLimitRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    @Operation(summary = "Create a rate limit rule")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    @ApiResponse(responseCode = "409", description = "Duplicate identifier/namespace")
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        RuleResponse created = ruleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List rate limit rules")
    @ApiResponse(responseCode = "200", description = "OK")
    public List<RuleResponse> list(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIST_LIMIT) int limit) {
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return ruleService.list(limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a rate limit rule by id")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Not found")
    public RuleResponse get(@PathVariable Long id) {
        return ruleService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a rate limit rule")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "Duplicate identifier/namespace")
    public RuleResponse update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        return ruleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a rate limit rule")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found")
    public void delete(@PathVariable Long id) {
        ruleService.delete(id);
    }
}
