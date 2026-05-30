package com.fleet.maintenance.infrastructure.kafka;

import com.fleet.maintenance.domain.event.DomainEvent;

public record KafkaEventEnvelope(
    String eventId,
    String eventType,
    String timestamp,
    String correlationId,
    Object payload
) {

    public static KafkaEventEnvelope from(DomainEvent event) {
        return new KafkaEventEnvelope(
            event.eventId().toString(),
            event.eventType(),
            event.timestamp().toString(),
            event.correlationId().toString(),
            event
        );
    }
}
