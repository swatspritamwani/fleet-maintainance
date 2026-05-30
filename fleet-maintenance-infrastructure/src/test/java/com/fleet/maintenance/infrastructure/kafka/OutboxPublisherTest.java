package com.fleet.maintenance.infrastructure.kafka;

import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import com.fleet.maintenance.infrastructure.repository.DynamoDbOutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int DLQ_THRESHOLD = 7;

    @Mock
    private DynamoDbOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate);
    }

    @Test
    void publishesPendingEventsAndMarksPublished() {
        OutboxEventRecord event = buildEvent("evt-1", "maintenance.request.created", "req-1");
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(buildSendResult("maintenance.request.created")));

        publisher.publishPending();

        verify(kafkaTemplate).send("maintenance.request.created", "req-1", "{payload}");
        verify(outboxRepository).markPublished("evt-1");
    }

    @Test
    void doesNothingWhenNoPendingEvents() {
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of());

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void incrementsRetryOnKafkaFailure() {
        OutboxEventRecord event = buildEvent("evt-2", "maintenance.request.created", "req-2");
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);
        when(outboxRepository.incrementRetry("evt-2")).thenReturn(1);

        publisher.publishPending();

        verify(outboxRepository).incrementRetry("evt-2");
        verify(outboxRepository, never()).markPublished(anyString());
    }

    @Test
    void sendsToDlqAfterThresholdRetries() {
        OutboxEventRecord event = buildEvent("evt-3", "maintenance.payment.ready", "req-3");
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(eq("maintenance.payment.ready"), anyString(), anyString())).thenReturn(failed);
        when(outboxRepository.incrementRetry("evt-3")).thenReturn(DLQ_THRESHOLD);
        when(kafkaTemplate.send(eq("maintenance.events.dlq"), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(buildSendResult("maintenance.events.dlq")));

        publisher.publishPending();

        verify(kafkaTemplate).send(eq("maintenance.events.dlq"), eq("req-3"), anyString());
        verify(outboxRepository).markDeadLetter("evt-3");
    }

    @Test
    void publishesMultipleEventsInBatch() {
        OutboxEventRecord evt1 = buildEvent("evt-a", "maintenance.request.created", "req-a");
        OutboxEventRecord evt2 = buildEvent("evt-b", "maintenance.request.assigned", "req-b");
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of(evt1, evt2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(buildSendResult("topic")));

        publisher.publishPending();

        verify(outboxRepository).markPublished("evt-a");
        verify(outboxRepository).markPublished("evt-b");
    }

    private OutboxEventRecord buildEvent(String eventId, String topic, String messageKey) {
        OutboxEventRecord record = new OutboxEventRecord();
        record.setEventId(eventId);
        record.setKafkaTopic(topic);
        record.setMessageKey(messageKey);
        record.setPayload("{payload}");
        record.setRetryCount(0);
        return record;
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> buildSendResult(String topic) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "key", "value");
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        return new SendResult<>(producerRecord, metadata);
    }
}
