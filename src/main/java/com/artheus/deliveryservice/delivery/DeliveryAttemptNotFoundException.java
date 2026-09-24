package com.artheus.deliveryservice.delivery;

import com.artheus.deliveryservice.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DeliveryAttemptNotFoundException extends ApplicationException {
    public DeliveryAttemptNotFoundException(UUID publicId) {
        super("Delivery not found: " + publicId, HttpStatus.NOT_FOUND);
    }
}
