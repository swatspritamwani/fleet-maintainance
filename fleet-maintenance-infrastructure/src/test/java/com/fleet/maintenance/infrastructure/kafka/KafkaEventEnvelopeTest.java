package com.fleet.maintenance.infrastructure.kafka;

import com.fleet.maintenance.domain.event.RequestCreatedEvent;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventEnvelopeTest {

    @Test
    void fromCreatesEnvelopeWithCorrectFields() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-001", "Oil change", Priority.LOW, "coord-1", correlationId);
        List<com.fleet.maintenance.domain.event.DomainEvent> events = request.pullDomainEvents();
        RequestCreatedEvent event = (RequestCreatedEvent) events.get(0);

        KafkaEventEnvelope envelope = KafkaEventEnvelope.from(event);

        assertThat(envelope.eventId()).isEqualTo(event.eventId().toString());
        assertThat(envelope.eventType()).isEqualTo(RequestCreatedEvent.EVENT_TYPE);
        assertThat(envelope.correlationId()).isEqualTo(correlationId.toString());
        assertThat(envelope.payload()).isSameAs(event);
    }

    @Test
    void envelopeIsImmutableRecord() {
        UUID correlationId = UUID.randomUUID();
        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
            "eid", "maintenance.request.created", "2026-05-30T00:00:00Z", correlationId.toString(), "payload");

        assertThat(envelope.eventId()).isEqualTo("eid");
        assertThat(envelope.eventType()).isEqualTo("maintenance.request.created");
    }
}
