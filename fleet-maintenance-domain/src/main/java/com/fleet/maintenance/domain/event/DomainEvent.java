package com.fleet.maintenance.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent
    permits RequestCreatedEvent,
            RequestAssignedEvent,
            InspectionSubmittedEvent,
            DecisionApprovedEvent,
            DecisionRejectedEvent,
            DecisionInfoRequestedEvent,
            PaymentReadinessEvent {

    UUID eventId();

    String eventType();

    Instant timestamp();

    UUID correlationId();

    UUID requestId();
}
