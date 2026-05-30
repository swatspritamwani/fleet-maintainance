package com.fleet.maintenance.infrastructure.repository;

import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.infrastructure.record.DecisionRecord;
import com.fleet.maintenance.infrastructure.record.InspectionReportRecord;
import com.fleet.maintenance.infrastructure.record.ServiceProviderRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbAdditionalRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    @SuppressWarnings("unchecked")
    private DynamoDbTable<InspectionReportRecord> inspTable;

    @Mock
    @SuppressWarnings("unchecked")
    private DynamoDbTable<DecisionRecord> decTable;

    @Mock
    @SuppressWarnings("unchecked")
    private DynamoDbTable<ServiceProviderRecord> spTable;

    private DynamoDbInspectionReportRepository inspectionRepo;
    private DynamoDbDecisionRepository decisionRepo;
    private DynamoDbServiceProviderRepository providerRepo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        doReturn(inspTable).when(enhancedClient).table(any(), any());
        inspectionRepo = new DynamoDbInspectionReportRepository(enhancedClient, "fleet-maintenance");

        doReturn(decTable).when(enhancedClient).table(any(), any());
        decisionRepo = new DynamoDbDecisionRepository(enhancedClient, "fleet-maintenance");

        doReturn(spTable).when(enhancedClient).table(any(), any());
        providerRepo = new DynamoDbServiceProviderRepository(enhancedClient, "fleet-maintenance");
    }

    @Test
    void inspectionRepoFromRecordMapsAllFields() {
        InspectionReportRecord rec = buildInspectionRecord();
        Page<InspectionReportRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(rec));
        when(inspTable.query(any(software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.class))).thenReturn(() -> Collections.singletonList(page).iterator());

        List<InspectionReport> reports = inspectionRepo.findByRequestId(UUID.randomUUID());

        assertThat(reports).hasSize(1);
        InspectionReport report = reports.get(0);
        assertThat(report.findings()).isEqualTo("Oil low");
        assertThat(report.estimatedCost().currency()).isEqualTo("USD");
        assertThat(report.estimatedDurationDays()).isEqualTo(1);
    }

    @Test
    void inspectionRepoFindLatestByRequestIdReturnsLatest() {
        InspectionReportRecord rec = buildInspectionRecord();
        Page<InspectionReportRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(rec));
        when(inspTable.query(any(software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.class))).thenReturn(() -> Collections.singletonList(page).iterator());

        Optional<InspectionReport> result = inspectionRepo.findLatestByRequestId(UUID.randomUUID());

        assertThat(result).isPresent();
    }

    @Test
    void inspectionRepoFindLatestReturnsEmptyWhenNoResults() {
        Page<InspectionReportRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of());
        when(inspTable.query(any(software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.class))).thenReturn(() -> Collections.singletonList(page).iterator());

        Optional<InspectionReport> result = inspectionRepo.findLatestByRequestId(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void decisionRepoFromRecordMapsAllFields() {
        DecisionRecord rec = new DecisionRecord();
        rec.setDecisionId(UUID.randomUUID().toString());
        rec.setRequestId(UUID.randomUUID().toString());
        rec.setOutcome("REJECTED");
        rec.setRemarks("Need more info");
        rec.setDecidedBy("coord-1");
        rec.setDecidedAt(Instant.now().toString());
        Page<DecisionRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(rec));
        when(decTable.query(any(software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.class)))
            .thenReturn(() -> Collections.singletonList(page).iterator());

        List<Decision> decisions = decisionRepo.findByRequestId(UUID.randomUUID());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).outcome()).isEqualTo(DecisionOutcome.REJECTED);
        assertThat(decisions.get(0).remarks()).isEqualTo("Need more info");
    }

    @Test
    void providerRepoFindByIdReturnsPresent() {
        UUID providerId = UUID.randomUUID();
        ServiceProviderRecord rec = buildProviderRecord(providerId, true);
        when(spTable.getItem(any(Key.class))).thenReturn(rec);

        Optional<ServiceProvider> result = providerRepo.findById(providerId);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("AutoFix");
        assertThat(result.get().active()).isTrue();
    }

    @Test
    void providerRepoFindByIdReturnsEmptyWhenNotFound() {
        when(spTable.getItem(any(Key.class))).thenReturn(null);

        Optional<ServiceProvider> result = providerRepo.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void providerRepoFindAllActiveFiltersInactive() {
        ServiceProviderRecord active = buildProviderRecord(UUID.randomUUID(), true);
        ServiceProviderRecord inactive = buildProviderRecord(UUID.randomUUID(), false);
        Page<ServiceProviderRecord> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(active, inactive));
        when(spTable.scan(any(ScanEnhancedRequest.class)))
            .thenReturn(() -> Collections.singletonList(page).iterator());

        List<ServiceProvider> result = providerRepo.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).active()).isTrue();
    }

    private InspectionReportRecord buildInspectionRecord() {
        InspectionReportRecord rec = new InspectionReportRecord();
        rec.setPk("REQ#r1");
        rec.setSk("INSP#i1");
        rec.setReportId(UUID.randomUUID().toString());
        rec.setRequestId(UUID.randomUUID().toString());
        rec.setFindings("Oil low");
        rec.setEstimatedCostAmount("100.00");
        rec.setEstimatedCostCurrency("USD");
        rec.setEstimatedDurationDays(1);
        rec.setAttachments(List.of());
        rec.setSubmittedAt(Instant.now().toString());
        rec.setSubmittedBy("prov-1");
        return rec;
    }

    private ServiceProviderRecord buildProviderRecord(UUID providerId, boolean active) {
        ServiceProviderRecord rec = new ServiceProviderRecord();
        rec.setProviderId(providerId.toString());
        rec.setName("AutoFix");
        rec.setContactEmail("fix@example.com");
        rec.setPhone("555-0100");
        rec.setActive(active);
        return rec;
    }
}
