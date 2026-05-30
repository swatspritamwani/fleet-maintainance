package com.fleet.maintenance.infrastructure.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.event.EventSummary;
import com.fleet.maintenance.domain.port.EventRepository;
import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DynamoDbEventRepository implements EventRepository {

    private static final String INDEX_NAME = "status-createdAt-index";

    private final DynamoDbIndex<OutboxEventRecord> statusIndex;
    private final ObjectMapper objectMapper;

    public DynamoDbEventRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.outbox-table-name:fleet-maintenance-outbox}") String outboxTableName,
            ObjectMapper objectMapper) {
        DynamoDbTable<OutboxEventRecord> outboxTable =
            enhancedClient.table(outboxTableName, TableSchema.fromBean(OutboxEventRecord.class));
        this.statusIndex = outboxTable.index(INDEX_NAME);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EventSummary> findPublished(String eventType, Instant since, int offset, int limit) {
        return queryPublished(since).stream()
            .filter(r -> eventType == null || eventType.equals(r.getEventType()))
            .skip(offset)
            .limit(limit)
            .map(this::toSummary)
            .toList();
    }

    @Override
    public long countPublished(String eventType, Instant since) {
        return queryPublished(since).stream()
            .filter(r -> eventType == null || eventType.equals(r.getEventType()))
            .count();
    }

    private List<OutboxEventRecord> queryPublished(Instant since) {
        QueryConditional condition = since != null
            ? QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder()
                    .partitionValue(DynamoDbOutboxRepository.PUBLISHED)
                    .sortValue(since.toString())
                    .build())
            : QueryConditional.keyEqualTo(
                k -> k.partitionValue(DynamoDbOutboxRepository.PUBLISHED));
        return statusIndex.query(QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .toList();
    }

    EventSummary toSummary(OutboxEventRecord rec) {
        UUID correlationId = null;
        Instant timestamp = null;
        try {
            JsonNode envelope = objectMapper.readTree(rec.getPayload());
            String corr = envelope.path("correlationId").asText(null);
            if (corr != null && !corr.isEmpty()) {
                correlationId = UUID.fromString(corr);
            }
            String ts = envelope.path("timestamp").asText(null);
            if (ts != null && !ts.isEmpty()) {
                timestamp = Instant.parse(ts);
            }
        } catch (Exception ignored) {
        }
        if (timestamp == null) {
            timestamp = Instant.parse(rec.getCreatedAt());
        }
        return new EventSummary(
            UUID.fromString(rec.getEventId()),
            rec.getEventType(),
            timestamp,
            correlationId,
            rec.getPayload()
        );
    }
}
