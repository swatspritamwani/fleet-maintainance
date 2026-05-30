package com.fleet.maintenance.infrastructure.repository;

import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbOutboxRepositoryTest {

    private static final int FIND_BATCH_SIZE = 10;
    private static final int INITIAL_RETRY_COUNT = 2;
    private static final int EXPECTED_RETRY_COUNT = 3;

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    @SuppressWarnings("unchecked")
    private DynamoDbTable<OutboxEventRecord> outboxTable;

    @Mock
    @SuppressWarnings("unchecked")
    private DynamoDbIndex<OutboxEventRecord> statusIndex;

    private DynamoDbOutboxRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        doReturn(outboxTable).when(enhancedClient).table(anyString(), any());
        when(outboxTable.index(anyString())).thenReturn(statusIndex);
        repository = new DynamoDbOutboxRepository(enhancedClient, "fleet-maintenance-outbox");
    }

    @Test
    void savePersistsRecord() {
        OutboxEventRecord record = buildRecord("evt-save");

        repository.save(record);

        verify(outboxTable).putItem(record);
    }

    @Test
    void findPendingReturnsEventsFromIndex() {
        OutboxEventRecord record = buildRecord("evt-1");
        Page<OutboxEventRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(record));
        when(statusIndex.query(any(QueryEnhancedRequest.class)))
            .thenReturn(() -> {
                Iterator<Page<OutboxEventRecord>> it = Collections.singletonList(page).iterator();
                return it;
            });

        List<OutboxEventRecord> result = repository.findPending(FIND_BATCH_SIZE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo("evt-1");
    }

    @Test
    void markPublishedUpdatesStatusAndPublishedAt() {
        OutboxEventRecord record = buildRecord("evt-2");
        when(outboxTable.getItem(any(Key.class))).thenReturn(record);

        repository.markPublished("evt-2");

        assertThat(record.getStatus()).isEqualTo(DynamoDbOutboxRepository.PUBLISHED);
        assertThat(record.getPublishedAt()).isNotNull();
        verify(outboxTable).putItem(record);
    }

    @Test
    void markDeadLetterUpdatesStatus() {
        OutboxEventRecord record = buildRecord("evt-3");
        when(outboxTable.getItem(any(Key.class))).thenReturn(record);

        repository.markDeadLetter("evt-3");

        assertThat(record.getStatus()).isEqualTo(DynamoDbOutboxRepository.DEAD_LETTER);
        verify(outboxTable).putItem(record);
    }

    @Test
    void incrementRetryIncrementsCountAndReturnsNewValue() {
        OutboxEventRecord record = buildRecord("evt-4");
        record.setRetryCount(INITIAL_RETRY_COUNT);
        when(outboxTable.getItem(any(Key.class))).thenReturn(record);

        int result = repository.incrementRetry("evt-4");

        assertThat(result).isEqualTo(EXPECTED_RETRY_COUNT);
        assertThat(record.getRetryCount()).isEqualTo(EXPECTED_RETRY_COUNT);
        verify(outboxTable).putItem(record);
    }

    @Test
    void markPublishedDoesNothingWhenRecordNotFound() {
        when(outboxTable.getItem(any(Key.class))).thenReturn(null);

        repository.markPublished("nonexistent");

        verify(outboxTable).getItem(any(Key.class));
    }

    @Test
    void incrementRetryReturnsZeroWhenRecordNotFound() {
        when(outboxTable.getItem(any(Key.class))).thenReturn(null);

        int result = repository.incrementRetry("nonexistent");

        assertThat(result).isEqualTo(0);
    }

    private OutboxEventRecord buildRecord(String eventId) {
        OutboxEventRecord rec = new OutboxEventRecord();
        rec.setEventId(eventId);
        rec.setPk("OUTBOX#" + eventId);
        rec.setSk("OUTBOX#" + eventId);
        rec.setStatus(DynamoDbOutboxRepository.PENDING);
        rec.setRetryCount(0);
        return rec;
    }
}
