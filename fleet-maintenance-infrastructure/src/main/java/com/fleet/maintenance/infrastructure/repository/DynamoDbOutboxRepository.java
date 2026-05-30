package com.fleet.maintenance.infrastructure.repository;

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

@Component
public class DynamoDbOutboxRepository {

    public static final String PENDING = "PENDING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DEAD_LETTER = "DEAD_LETTER";
    private static final String OUTBOX_PREFIX = "OUTBOX#";
    private static final String INDEX_NAME = "status-createdAt-index";

    private final DynamoDbTable<OutboxEventRecord> outboxTable;
    private final DynamoDbIndex<OutboxEventRecord> statusIndex;

    public DynamoDbOutboxRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.outbox-table-name:fleet-maintenance-outbox}") String outboxTableName) {
        this.outboxTable = enhancedClient.table(outboxTableName, TableSchema.fromBean(OutboxEventRecord.class));
        this.statusIndex = outboxTable.index(INDEX_NAME);
    }

    public void save(OutboxEventRecord record) {
        outboxTable.putItem(record);
    }

    public List<OutboxEventRecord> findPending(int maxBatch) {
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(PENDING));
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
            .queryConditional(condition)
            .limit(maxBatch)
            .build();
        return statusIndex.query(request)
            .stream()
            .flatMap(page -> page.items().stream())
            .toList();
    }

    public void markPublished(String eventId) {
        updateStatus(eventId, PUBLISHED, Instant.now().toString());
    }

    public void markDeadLetter(String eventId) {
        updateStatus(eventId, DEAD_LETTER, null);
    }

    public int incrementRetry(String eventId) {
        String keyValue = OUTBOX_PREFIX + eventId;
        Key key = Key.builder().partitionValue(keyValue).sortValue(keyValue).build();
        OutboxEventRecord record = outboxTable.getItem(key);
        if (record == null) {
            return 0;
        }
        int newCount = record.getRetryCount() + 1;
        record.setRetryCount(newCount);
        outboxTable.putItem(record);
        return newCount;
    }

    private void updateStatus(String eventId, String newStatus, String publishedAt) {
        String keyValue = OUTBOX_PREFIX + eventId;
        Key key = Key.builder().partitionValue(keyValue).sortValue(keyValue).build();
        OutboxEventRecord record = outboxTable.getItem(key);
        if (record == null) {
            return;
        }
        record.setStatus(newStatus);
        if (publishedAt != null) {
            record.setPublishedAt(publishedAt);
        }
        outboxTable.putItem(record);
    }
}
