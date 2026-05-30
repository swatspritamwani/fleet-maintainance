package com.fleet.maintenance.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.valueobject.Money;
import com.fleet.maintenance.infrastructure.kafka.KafkaEventEnvelope;
import com.fleet.maintenance.infrastructure.record.DecisionRecord;
import com.fleet.maintenance.infrastructure.record.InspectionReportRecord;
import com.fleet.maintenance.infrastructure.record.MaintenanceRequestRecord;
import com.fleet.maintenance.infrastructure.record.OutboxEventRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DynamoDbMaintenanceRequestRepository implements MaintenanceRequestRepository {

    static final String REQ_PREFIX = "REQ#";
    static final String INSP_PREFIX = "INSP#";
    static final String DEC_PREFIX = "DEC#";
    static final String OUTBOX_PREFIX = "OUTBOX#";
    private static final String STATUS_INDEX = "status-createdAt-index";
    private static final int OUTBOX_TTL_DAYS = 7;

    private final DynamoDbTable<MaintenanceRequestRecord> requestTable;
    private final DynamoDbTable<InspectionReportRecord> inspectionTable;
    private final DynamoDbTable<DecisionRecord> decisionTable;
    private final DynamoDbTable<OutboxEventRecord> outboxTable;
    private final DynamoDbIndex<MaintenanceRequestRecord> statusIndex;
    private final DynamoDbEnhancedClient enhancedClient;
    private final ObjectMapper objectMapper;

    public DynamoDbMaintenanceRequestRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.table-name:fleet-maintenance}") String tableName,
            @Value("${fleet.dynamodb.outbox-table-name:fleet-maintenance-outbox}") String outboxTableName,
            ObjectMapper objectMapper) {
        this.enhancedClient = enhancedClient;
        this.objectMapper = objectMapper;
        this.requestTable = enhancedClient.table(tableName, TableSchema.fromBean(MaintenanceRequestRecord.class));
        this.inspectionTable = enhancedClient.table(tableName, TableSchema.fromBean(InspectionReportRecord.class));
        this.decisionTable = enhancedClient.table(tableName, TableSchema.fromBean(DecisionRecord.class));
        this.outboxTable = enhancedClient.table(outboxTableName, TableSchema.fromBean(OutboxEventRecord.class));
        this.statusIndex = requestTable.index(STATUS_INDEX);
    }

    @Override
    public void saveWithEvents(MaintenanceRequest request, List<DomainEvent> events) {
        MaintenanceRequestRecord reqRec = toRequestRecord(request);
        List<OutboxEventRecord> outboxRecs = toOutboxRecords(events);
        enhancedClient.transactWriteItems(b -> {
            b.addPutItem(requestTable, reqRec);
            outboxRecs.forEach(rec -> b.addPutItem(outboxTable, rec));
        });
    }

    @Override
    public void saveWithInspectionAndEvents(
            MaintenanceRequest request, InspectionReport report, List<DomainEvent> events) {
        MaintenanceRequestRecord reqRec = toRequestRecord(request);
        InspectionReportRecord inspRec = toInspectionRecord(report);
        List<OutboxEventRecord> outboxRecs = toOutboxRecords(events);
        enhancedClient.transactWriteItems(b -> {
            b.addPutItem(requestTable, reqRec);
            b.addPutItem(inspectionTable, inspRec);
            outboxRecs.forEach(rec -> b.addPutItem(outboxTable, rec));
        });
    }

    @Override
    public void saveWithDecisionAndEvents(
            MaintenanceRequest request, Decision decision, List<DomainEvent> events) {
        MaintenanceRequestRecord reqRec = toRequestRecord(request);
        DecisionRecord decRec = toDecisionRecord(decision);
        List<OutboxEventRecord> outboxRecs = toOutboxRecords(events);
        enhancedClient.transactWriteItems(b -> {
            b.addPutItem(requestTable, reqRec);
            b.addPutItem(decisionTable, decRec);
            outboxRecs.forEach(rec -> b.addPutItem(outboxTable, rec));
        });
    }

    @Override
    public Optional<MaintenanceRequest> findById(UUID requestId) {
        String keyValue = REQ_PREFIX + requestId;
        Key key = Key.builder().partitionValue(keyValue).sortValue(keyValue).build();
        MaintenanceRequestRecord record = requestTable.getItem(key);
        return Optional.ofNullable(record).map(this::fromRecord);
    }

    @Override
    public List<MaintenanceRequest> findByStatus(RequestStatus status) {
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(status.name()));
        return statusIndex.query(condition)
            .stream()
            .flatMap(page -> page.items().stream())
            .map(this::fromRecord)
            .toList();
    }

    @Override
    public List<MaintenanceRequest> findAll() {
        return requestTable.scan()
            .stream()
            .flatMap(page -> page.items().stream())
            .map(this::fromRecord)
            .toList();
    }

    MaintenanceRequestRecord toRequestRecord(MaintenanceRequest request) {
        MaintenanceRequestRecord rec = new MaintenanceRequestRecord();
        String keyValue = REQ_PREFIX + request.getRequestId();
        rec.setPk(keyValue);
        rec.setSk(keyValue);
        rec.setStatus(request.getStatus().name());
        rec.setCreatedAt(request.getCreatedAt().toString());
        rec.setRequestId(request.getRequestId().toString());
        rec.setVehicleId(request.getVehicleId());
        rec.setDescription(request.getDescription());
        rec.setPriority(request.getPriority().name());
        String pId = request.getAssignedProviderId() != null ? request.getAssignedProviderId().toString() : null;
        rec.setAssignedProviderId(pId);
        rec.setCreatedBy(request.getCreatedBy());
        rec.setUpdatedAt(request.getUpdatedAt().toString());
        return rec;
    }

    MaintenanceRequest fromRecord(MaintenanceRequestRecord rec) {
        UUID assignedProviderId = rec.getAssignedProviderId() != null
            ? UUID.fromString(rec.getAssignedProviderId()) : null;
        return MaintenanceRequest.reconstitute(
            UUID.fromString(rec.getRequestId()),
            rec.getVehicleId(),
            rec.getDescription(),
            Priority.valueOf(rec.getPriority()),
            RequestStatus.valueOf(rec.getStatus()),
            assignedProviderId,
            rec.getCreatedBy(),
            Instant.parse(rec.getCreatedAt()),
            Instant.parse(rec.getUpdatedAt())
        );
    }

    private InspectionReportRecord toInspectionRecord(InspectionReport report) {
        InspectionReportRecord rec = new InspectionReportRecord();
        rec.setPk(REQ_PREFIX + report.requestId());
        rec.setSk(INSP_PREFIX + report.reportId());
        rec.setReportId(report.reportId().toString());
        rec.setRequestId(report.requestId().toString());
        rec.setFindings(report.findings());
        rec.setEstimatedCostAmount(report.estimatedCost().amount().toPlainString());
        rec.setEstimatedCostCurrency(report.estimatedCost().currency());
        rec.setEstimatedDurationDays(report.estimatedDurationDays());
        rec.setAttachments(report.attachments());
        rec.setSubmittedAt(report.submittedAt().toString());
        rec.setSubmittedBy(report.submittedBy());
        return rec;
    }

    private DecisionRecord toDecisionRecord(Decision decision) {
        DecisionRecord rec = new DecisionRecord();
        rec.setPk(REQ_PREFIX + decision.requestId());
        rec.setSk(DEC_PREFIX + decision.decisionId());
        rec.setDecisionId(decision.decisionId().toString());
        rec.setRequestId(decision.requestId().toString());
        rec.setOutcome(decision.outcome().name());
        rec.setRemarks(decision.remarks());
        rec.setDecidedBy(decision.decidedBy());
        rec.setDecidedAt(decision.decidedAt().toString());
        return rec;
    }

    OutboxEventRecord toOutboxRecord(DomainEvent event) {
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

    private List<OutboxEventRecord> toOutboxRecords(List<DomainEvent> events) {
        return events.stream().map(this::toOutboxRecord).toList();
    }

    InspectionReport fromInspectionRecord(InspectionReportRecord rec) {
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
}
