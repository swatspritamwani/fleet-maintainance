package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record RequestCreatedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    String vehicleId,
    String priority,
    String createdBy
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.request.created";

    public static RequestCreatedEvent of(MaintenanceRequest request, UUID correlationId) {
        return new RequestCreatedEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            Instant.now(),
            correlationId,
            request.getRequestId(),
            request.getVehicleId(),
            request.getPriority().name(),
            request.getCreatedBy()
        );
    }
}
