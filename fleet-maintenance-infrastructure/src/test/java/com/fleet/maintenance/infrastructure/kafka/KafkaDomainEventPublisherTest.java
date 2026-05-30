package com.fleet.maintenance.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import com.fleet.maintenance.infrastructure.repository.DynamoDbOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaDomainEventPublisherTest {

    @Mock
    private DynamoDbOutboxRepository outboxRepository;

    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new KafkaDomainEventPublisher(outboxRepository, objectMapper);
    }

    @Test
    void publishWritesEventToOutbox() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-001", "Engine noise", Priority.HIGH, "coord-1", correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        DomainEvent event = events.get(0);

        publisher.publish(event, correlationId);

        ArgumentCaptor<OutboxEventRecord> captor = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEventRecord saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.eventId().toString());
        assertThat(saved.getEventType()).isEqualTo("maintenance.request.created");
        assertThat(saved.getKafkaTopic()).isEqualTo("maintenance.request.created");
        assertThat(saved.getMessageKey()).isEqualTo(event.requestId().toString());
        assertThat(saved.getStatus()).isEqualTo(DynamoDbOutboxRepository.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getTtl()).isGreaterThan(0L);
    }

    @Test
    void publishEnvelopeContainsCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-002", "Tyre wear", Priority.LOW, "coord-2", correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        DomainEvent event = events.get(0);

        publisher.publish(event, correlationId);

        ArgumentCaptor<OutboxEventRecord> captor = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains(correlationId.toString());
    }

    @Test
    void publishSetsOutboxPkAndSk() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-003", "Oil change", Priority.MEDIUM, "coord-3", correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        DomainEvent event = events.get(0);

        publisher.publish(event, correlationId);

        ArgumentCaptor<OutboxEventRecord> captor = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEventRecord saved = captor.getValue();
        assertThat(saved.getPk()).isEqualTo("OUTBOX#" + event.eventId());
        assertThat(saved.getSk()).isEqualTo("OUTBOX#" + event.eventId());
    }
}
