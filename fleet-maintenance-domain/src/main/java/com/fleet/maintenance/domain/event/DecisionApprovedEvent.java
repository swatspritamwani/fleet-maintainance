package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record DecisionApprovedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    UUID decisionId,
    String decidedBy
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.decision.approved";

    public static DecisionApprovedEvent of(
            MaintenanceRequest request,
            Decision decision,
            UUID correlationId) {
        return new DecisionApprovedEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            Instant.now(),
            correlationId,
            request.getRequestId(),
            decision.decisionId(),
            decision.decidedBy()
        );
    }
}
