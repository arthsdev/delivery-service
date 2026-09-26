package com.artheus.deliveryservice.delivery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/app/v1/deliveries")
@RequiredArgsConstructor
@Tag(name = "Deliveries", description = "Receives delivery attempts and processes them asynchronously")
public class DeliveryController {

    private final DeliveryAttemptService deliveryAttemptService;

    @Operation(
            summary = "Request a delivery attempt",
            description = "Accepts a delivery request and processes it asynchronously in the background. Returns immediately with 202 Accepted; the actual HTTP call to the subscriber's targetUrl happens after the response is sent."
    )
    @ApiResponse(responseCode = "202", description = "Delivery request accepted for asynchronous processing")
    @ApiResponse(responseCode = "400", description = "Invalid payload")
    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid DeliveryRequest request) {
        DeliveryResponse response = deliveryAttemptService.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Find a delivery attempt by its publicId")
    @ApiResponse(responseCode = "200", description = "Delivery attempt found")
    @ApiResponse(responseCode = "404", description = "Delivery attempt not found")
    @GetMapping("/{publicId}")
    public ResponseEntity<DeliveryResponse> findByPublicId(@PathVariable UUID publicId) {
        return ResponseEntity.ok(deliveryAttemptService.findByPublicId(publicId));
    }
}