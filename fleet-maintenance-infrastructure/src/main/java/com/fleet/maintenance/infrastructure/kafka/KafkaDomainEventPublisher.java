package com.fleet.maintenance.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.port.DomainEventPublisher;
import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import com.fleet.maintenance.infrastructure.repository.DynamoDbOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final int OUTBOX_TTL_DAYS = 7;
    private static final String OUTBOX_PREFIX = "OUTBOX#";

    private final DynamoDbOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaDomainEventPublisher(
            DynamoDbOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event, UUID correlationId) {
        outboxRepository.save(toOutboxRecord(event));
    }

    private OutboxEventRecord toOutboxRecord(DomainEvent event) {
        try {
            KafkaEventEnvelope envelope = KafkaEventEnvelope.from(event);
            String payloadJson = objectMapper.writeValueAsString(envelope);
            Instant now = Instant.now();
            long ttl = now.plus(OUTBOX_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();
            OutboxEventRecord rec = new OutboxEventRecord();
            rec.setPk(OUTBOX_PREFIX + event.eventId());
            rec.setSk(OUTBOX_PREFIX + event.eventId());
            rec.setStatus(DynamoDbOutboxRepository.PENDING);
            rec.setCreatedAt(now.toString());
            rec.setEventId(event.eventId().toString());
            rec.setEventType(event.eventType());
            rec.setKafkaTopic(event.eventType());
            rec.setMessageKey(event.requestId().toString());
            rec.setPayload(payloadJson);
            rec.setRetryCount(0);
            rec.setTtl(ttl);
            return rec;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + event.eventId(), e);
        }
    }
}
