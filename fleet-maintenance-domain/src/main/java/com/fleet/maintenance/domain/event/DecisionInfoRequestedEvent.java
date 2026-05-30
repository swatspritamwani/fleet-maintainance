package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record DecisionInfoRequestedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    UUID decisionId,
    String decidedBy,
    String remarks
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.decision.info-requested";

    public static DecisionInfoRequestedEvent of(
            MaintenanceRequest request,
            Decision decision,
            UUID correlationId) {
        return new DecisionInfoRequestedEvent(
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
