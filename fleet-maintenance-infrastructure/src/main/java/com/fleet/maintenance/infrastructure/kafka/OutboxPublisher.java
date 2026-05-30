package com.fleet.maintenance.infrastructure.kafka;

import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import com.fleet.maintenance.infrastructure.repository.DynamoDbOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_BATCH_SIZE = 50;
    private static final int DLQ_RETRY_THRESHOLD = 7;
    private static final String DLQ_TOPIC = "maintenance.events.dlq";

    private final DynamoDbOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            DynamoDbOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPending() {
        List<OutboxEventRecord> pending = outboxRepository.findPending(MAX_BATCH_SIZE);
        for (OutboxEventRecord event : pending) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEventRecord event) {
        try {
            kafkaTemplate.send(event.getKafkaTopic(), event.getMessageKey(), event.getPayload()).get();
            outboxRepository.markPublished(event.getEventId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing event {}", event.getEventId());
        } catch (ExecutionException e) {
            handlePublishFailure(event, e.getCause());
        }
    }

    private void handlePublishFailure(OutboxEventRecord event, Throwable cause) {
        int retryCount = outboxRepository.incrementRetry(event.getEventId());
        log.warn("Outbox publish failed for event {} (retry {}): {}",
            event.getEventId(), retryCount, cause != null ? cause.getMessage() : "unknown");
        if (retryCount >= DLQ_RETRY_THRESHOLD) {
            kafkaTemplate.send(DLQ_TOPIC, event.getMessageKey(), event.getPayload());
            outboxRepository.markDeadLetter(event.getEventId());
            log.error("Event {} sent to DLQ after {} retries", event.getEventId(), retryCount);
        }
    }
}
