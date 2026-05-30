package com.fleet.maintenance.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.event.RequestCreatedEvent;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.valueobject.Money;
import com.fleet.maintenance.infrastructure.record.DecisionRecord;
import com.fleet.maintenance.infrastructure.record.InspectionReportRecord;
import com.fleet.maintenance.infrastructure.record.MaintenanceRequestRecord;
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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbMaintenanceRequestRepositoryTest {

    private static final int SAMPLE_COST = 100;

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @SuppressWarnings("unchecked")
    private DynamoDbTable<MaintenanceRequestRecord> reqTable;
    @SuppressWarnings("unchecked")
    private DynamoDbIndex<MaintenanceRequestRecord> statusIndex;

    private DynamoDbMaintenanceRequestRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reqTable = mock(DynamoDbTable.class);
        DynamoDbTable<InspectionReportRecord> inspTable = mock(DynamoDbTable.class);
        DynamoDbTable<DecisionRecord> decTable = mock(DynamoDbTable.class);
        DynamoDbTable<OutboxEventRecord> outTable = mock(DynamoDbTable.class);
        statusIndex = mock(DynamoDbIndex.class);
        doReturn(reqTable).doReturn(inspTable).doReturn(decTable).doReturn(outTable)
            .when(enhancedClient).table(anyString(), any());
        when(reqTable.index(anyString())).thenReturn(statusIndex);
        repository = new DynamoDbMaintenanceRequestRepository(
            enhancedClient, "fleet-maintenance", "fleet-maintenance-outbox", objectMapper);
    }

    @Test
    void toRequestRecordSetsAllFields() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-001", "Engine oil", Priority.HIGH, "coord-1", correlationId);
        request.pullDomainEvents();

        MaintenanceRequestRecord record = repository.toRequestRecord(request);

        assertThat(record.getPk()).startsWith(DynamoDbMaintenanceRequestRepository.REQ_PREFIX);
        assertThat(record.getSk()).isEqualTo(record.getPk());
        assertThat(record.getVehicleId()).isEqualTo("VH-001");
        assertThat(record.getPriority()).isEqualTo("HIGH");
        assertThat(record.getStatus()).isEqualTo("CREATED");
        assertThat(record.getCreatedBy()).isEqualTo("coord-1");
        assertThat(record.getAssignedProviderId()).isNull();
    }

    @Test
    void fromRecordReconstitutesRequest() {
        MaintenanceRequestRecord record = new MaintenanceRequestRecord();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        record.setRequestId(requestId.toString());
        record.setVehicleId("VH-002");
        record.setDescription("Test");
        record.setPriority("MEDIUM");
        record.setStatus("CREATED");
        record.setCreatedBy("coord-2");
        record.setCreatedAt(now.toString());
        record.setUpdatedAt(now.toString());

        MaintenanceRequest request = repository.fromRecord(record);

        assertThat(request.getRequestId()).isEqualTo(requestId);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(request.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(request.pullDomainEvents()).isEmpty();
    }

    @Test
    void toOutboxRecordSetsKafkaFields() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-003", "Test", Priority.LOW, "coord-1", correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        DomainEvent event = events.get(0);

        OutboxEventRecord record = repository.toOutboxRecord(event);

        assertThat(record.getPk()).startsWith(DynamoDbMaintenanceRequestRepository.OUTBOX_PREFIX);
        assertThat(record.getStatus()).isEqualTo("PENDING");
        assertThat(record.getKafkaTopic()).isEqualTo(RequestCreatedEvent.EVENT_TYPE);
        assertThat(record.getPayload()).isNotBlank();
        assertThat(record.getRetryCount()).isEqualTo(0);
        assertThat(record.getTtl()).isGreaterThan(0L);
    }

    @Test
    void toRequestRecordWithAssignedProvider() {
        UUID correlationId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-004", "Test", Priority.CRITICAL, "coord-1", correlationId);
        request.assign(providerId, correlationId);
        request.pullDomainEvents();

        MaintenanceRequestRecord record = repository.toRequestRecord(request);

        assertThat(record.getAssignedProviderId()).isEqualTo(providerId.toString());
        assertThat(record.getStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByStatusQueriesGsiAndReturnsEmpty() {
        Page<MaintenanceRequestRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of());
        when(statusIndex.query(any(QueryConditional.class)))
            .thenReturn(() -> Collections.singletonList(page).iterator());

        List<MaintenanceRequest> results = repository.findByStatus(RequestStatus.CREATED);

        assertThat(results).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByStatusReturnsMatchingRequests() {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        MaintenanceRequestRecord record = new MaintenanceRequestRecord();
        record.setRequestId(requestId.toString());
        record.setVehicleId("VH-010");
        record.setDescription("Oil");
        record.setPriority("LOW");
        record.setStatus("CREATED");
        record.setCreatedBy("coord-1");
        record.setCreatedAt(now.toString());
        record.setUpdatedAt(now.toString());
        Page<MaintenanceRequestRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(record));
        when(statusIndex.query(any(QueryConditional.class)))
            .thenReturn(() -> Collections.singletonList(page).iterator());

        List<MaintenanceRequest> results = repository.findByStatus(RequestStatus.CREATED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRequestId()).isEqualTo(requestId);
    }

    @Test
    void fromInspectionRecordMapsFields() {
        InspectionReportRecord rec = new InspectionReportRecord();
        rec.setReportId(UUID.randomUUID().toString());
        rec.setRequestId(UUID.randomUUID().toString());
        rec.setFindings("test findings");
        rec.setEstimatedCostAmount("250.00");
        rec.setEstimatedCostCurrency("USD");
        rec.setEstimatedDurationDays(1);
        rec.setAttachments(List.of());
        rec.setSubmittedAt(Instant.now().toString());
        rec.setSubmittedBy("prov-1");

        InspectionReport report = repository.fromInspectionRecord(rec);

        assertThat(report.findings()).isEqualTo("test findings");
        assertThat(report.estimatedCost().currency()).isEqualTo("USD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWithEventsCallsTransactWrite() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-005", "Brake pads", Priority.HIGH, "coord-1", correlationId);
        List<DomainEvent> events = request.pullDomainEvents();

        repository.saveWithEvents(request, events);

        verify(enhancedClient).transactWriteItems(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWithInspectionAndEventsCallsTransactWrite() {
        UUID correlationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-006", "Oil change", Priority.MEDIUM, "coord-1", correlationId);
        request.assign(UUID.randomUUID(), correlationId);
        request.pullDomainEvents();
        InspectionReport report = new InspectionReport(
            UUID.randomUUID(), requestId, "Findings",
            Money.of(BigDecimal.valueOf(SAMPLE_COST)), 2, List.of(), Instant.now(), "prov-1");

        repository.saveWithInspectionAndEvents(request, report, List.of());

        verify(enhancedClient).transactWriteItems(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWithDecisionAndEventsCallsTransactWrite() {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-007", "Tyre", Priority.LOW, "coord-1", correlationId);
        request.assign(UUID.randomUUID(), correlationId);
        request.pullDomainEvents();
        Decision decision = new Decision(
            UUID.randomUUID(), request.getRequestId(), DecisionOutcome.REJECTED, "Too costly",
            "coord-1", Instant.now());

        repository.saveWithDecisionAndEvents(request, decision, List.of());

        verify(enhancedClient).transactWriteItems(any(Consumer.class));
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(reqTable.getItem(any(Key.class))).thenReturn(null);

        Optional<MaintenanceRequest> result = repository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdReturnsRequestWhenFound() {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        MaintenanceRequestRecord record = new MaintenanceRequestRecord();
        record.setRequestId(requestId.toString());
        record.setVehicleId("VH-008");
        record.setDescription("Test");
        record.setPriority("HIGH");
        record.setStatus("ASSIGNED");
        record.setCreatedBy("coord-1");
        record.setCreatedAt(now.toString());
        record.setUpdatedAt(now.toString());
        when(reqTable.getItem(any(Key.class))).thenReturn(record);

        Optional<MaintenanceRequest> result = repository.findById(requestId);

        assertThat(result).isPresent();
        assertThat(result.get().getRequestId()).isEqualTo(requestId);
        assertThat(result.get().getStatus()).isEqualTo(RequestStatus.ASSIGNED);
    }
}
