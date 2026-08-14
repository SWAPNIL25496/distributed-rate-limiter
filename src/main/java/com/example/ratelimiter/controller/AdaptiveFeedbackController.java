package com.example.ratelimiter.controller;

import com.example.ratelimiter.controller.dto.AdaptiveFeedbackRequest;
import com.example.ratelimiter.controller.dto.AdaptiveFeedbackResponse;
import com.example.ratelimiter.service.AdaptiveFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adaptive")
@Tag(name = "Adaptive", description = "Downstream error-rate feedback for temporary limit multipliers")
public class AdaptiveFeedbackController {

    private final AdaptiveFeedbackService adaptiveFeedbackService;

    public AdaptiveFeedbackController(AdaptiveFeedbackService adaptiveFeedbackService) {
        this.adaptiveFeedbackService = adaptiveFeedbackService;
    }

    @PostMapping("/feedback")
    @Operation(summary = "Report downstream error rate; set/refresh Redis adaptive multiplier (TTL 120s)")
    @ApiResponse(responseCode = "200", description = "Feedback accepted")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "No matching enabled rule")
    public AdaptiveFeedbackResponse feedback(@Valid @RequestBody AdaptiveFeedbackRequest request) {
        return adaptiveFeedbackService.feedback(request);
    }
}
