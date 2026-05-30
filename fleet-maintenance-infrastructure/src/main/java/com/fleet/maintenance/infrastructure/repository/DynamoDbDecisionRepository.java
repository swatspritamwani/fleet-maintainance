package com.fleet.maintenance.infrastructure.repository;

import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.port.DecisionRepository;
import com.fleet.maintenance.infrastructure.record.DecisionRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DynamoDbDecisionRepository implements DecisionRepository {

    private static final String REQ_PREFIX = "REQ#";
    private static final String DEC_PREFIX = "DEC#";

    private final DynamoDbTable<DecisionRecord> decisionTable;

    public DynamoDbDecisionRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.table-name:fleet-maintenance}") String tableName) {
        this.decisionTable = enhancedClient.table(tableName, TableSchema.fromBean(DecisionRecord.class));
    }

    @Override
    public List<Decision> findByRequestId(UUID requestId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
            k -> k.partitionValue(REQ_PREFIX + requestId).sortValue(DEC_PREFIX));
        return decisionTable.query(condition)
            .stream()
            .flatMap(page -> page.items().stream())
            .map(this::fromRecord)
            .toList();
    }

    private Decision fromRecord(DecisionRecord rec) {
        return new Decision(
            UUID.fromString(rec.getDecisionId()),
            UUID.fromString(rec.getRequestId()),
            DecisionOutcome.valueOf(rec.getOutcome()),
            rec.getRemarks(),
            rec.getDecidedBy(),
            Instant.parse(rec.getDecidedAt())
        );
    }
}
