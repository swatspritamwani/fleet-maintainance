package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record DecisionRejectedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    UUID decisionId,
    String decidedBy,
    String remarks
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.decision.rejected";

    public static DecisionRejectedEvent of(
            MaintenanceRequest request,
            Decision decision,
            UUID correlationId) {
        return new DecisionRejectedEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            Instant.now(),
            correlationId,
            request.getRequestId(),
            decision.decisionId(),
            decision.decidedBy(),
            decision.remarks()
        );
    }
}
