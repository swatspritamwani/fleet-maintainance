package com.fleet.maintenance.application.dto;

import com.fleet.maintenance.domain.model.Priority;

import java.util.Objects;
import java.util.UUID;

public record CreateRequestCommand(
    String vehicleId,
    String description,
    Priority priority,
    String createdBy,
    UUID correlationId
) {

    public CreateRequestCommand {
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
