package com.fleet.maintenance.infrastructure.repository;

import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.port.InspectionReportRepository;
import com.fleet.maintenance.domain.valueobject.Money;
import com.fleet.maintenance.infrastructure.record.InspectionReportRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DynamoDbInspectionReportRepository implements InspectionReportRepository {

    private static final String REQ_PREFIX = "REQ#";
    private static final String INSP_PREFIX = "INSP#";

    private final DynamoDbTable<InspectionReportRecord> inspectionTable;

    public DynamoDbInspectionReportRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.table-name:fleet-maintenance}") String tableName) {
        this.inspectionTable = enhancedClient.table(tableName, TableSchema.fromBean(InspectionReportRecord.class));
    }

    @Override
    public List<InspectionReport> findByRequestId(UUID requestId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
            k -> k.partitionValue(REQ_PREFIX + requestId).sortValue(INSP_PREFIX));
        return inspectionTable.query(condition)
            .stream()
            .flatMap(page -> page.items().stream())
            .map(this::fromRecord)
            .toList();
    }

    @Override
    public Optional<InspectionReport> findLatestByRequestId(UUID requestId) {
        return findByRequestId(requestId).stream()
            .max(Comparator.comparing(InspectionReport::submittedAt));
    }

    private InspectionReport fromRecord(InspectionReportRecord rec) {
        Money cost = Money.of(new BigDecimal(rec.getEstimatedCostAmount()), rec.getEstimatedCostCurrency());
        return new InspectionReport(
            UUID.fromString(rec.getReportId()),
            UUID.fromString(rec.getRequestId()),
            rec.getFindings(),
            cost,
            rec.getEstimatedDurationDays(),
            rec.getAttachments(),
            Instant.parse(rec.getSubmittedAt()),
            rec.getSubmittedBy()
        );
    }

    Key buildKey(UUID requestId, UUID reportId) {
        return Key.builder()
            .partitionValue(REQ_PREFIX + requestId)
            .sortValue(INSP_PREFIX + reportId)
            .build();
    }
}
