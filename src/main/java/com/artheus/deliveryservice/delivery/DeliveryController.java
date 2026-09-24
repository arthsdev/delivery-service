package com.artheus.deliveryservice.delivery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/app/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryAttemptService deliveryAttemptService;

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@RequestBody @Valid DeliveryRequest request) {
        DeliveryResponse response = deliveryAttemptService.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<DeliveryResponse> findByPublicId(@PathVariable UUID publicId) {
        return ResponseEntity.ok(deliveryAttemptService.findByPublicId(publicId));
    }
}