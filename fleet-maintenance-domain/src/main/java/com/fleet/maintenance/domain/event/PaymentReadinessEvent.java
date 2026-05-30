package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record PaymentReadinessEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    String vehicleId,
    UUID providerId,
    Money approvedCost,
    int estimatedDurationDays,
    String approvedBy,
    Instant approvedAt
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.payment.ready";

    public static PaymentReadinessEvent of(
            MaintenanceRequest request,
            Money approvedCost,
            int estimatedDurationDays,
            String approvedBy,
            UUID correlationId) {
        Instant now = Instant.now();
        return new PaymentReadinessEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            now,
            correlationId,
            request.getRequestId(),
            request.getVehicleId(),
            request.getAssignedProviderId(),
            approvedCost,
            estimatedDurationDays,
            approvedBy,
            now
        );
    }
}
