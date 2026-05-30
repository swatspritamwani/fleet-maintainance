package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record RequestAssignedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    String vehicleId,
    UUID providerId
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.request.assigned";

    public static RequestAssignedEvent of(MaintenanceRequest request, UUID correlationId) {
        return new RequestAssignedEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            Instant.now(),
            correlationId,
            request.getRequestId(),
            request.getVehicleId(),
            request.getAssignedProviderId()
        );
    }
}
