package com.fleet.maintenance.bff.mapper;

import com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response;
import com.fleet.maintenance.bff.dto.ListDecisions200ResponseInner;
import com.fleet.maintenance.bff.dto.ListInspections200ResponseInner;
import com.fleet.maintenance.bff.dto.ListServiceProviders200ResponseInner;
import com.fleet.maintenance.bff.dto.PagedEventDtoContentInner;
import com.fleet.maintenance.bff.dto.RequestDetailDto;
import com.fleet.maintenance.domain.event.EventSummary;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.valueobject.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(BffMapperTest.MapperConfig.class)
class BffMapperTest {

    @Configuration
    @ComponentScan(basePackages = "com.fleet.maintenance.bff.mapper")
    static class MapperConfig { }

    @Autowired
    private BffMapper mapper;

    private static final BigDecimal INSPECTION_COST = BigDecimal.valueOf(750.50);
    private static final int INSPECTION_DURATION = 5;
    private static final BigDecimal SIMPLE_COST = BigDecimal.valueOf(300);

    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @Test
    void toCreateResponse_mapsAllFields() {
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-001", "Engine oil change", Priority.HIGH, "coord-1", CORRELATION_ID);

        CreateMaintenanceRequest201Response dto = mapper.toCreateResponse(request);

        assertThat(dto.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(dto.getVehicleId()).isEqualTo("VH-001");
        assertThat(dto.getDescription()).isEqualTo("Engine oil change");
        assertThat(dto.getPriority().name()).isEqualTo("HIGH");
        assertThat(dto.getStatus().name()).isEqualTo("CREATED");
        assertThat(dto.getCreatedBy()).isEqualTo("coord-1");
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getUpdatedAt()).isNotNull();
        assertThat(dto.getAssignedProviderId()).isNull();
    }

    @Test
    void toCreateResponse_priorityEnum_mapsCorrectly() {
        for (Priority p : Priority.values()) {
            MaintenanceRequest req = MaintenanceRequest.create(
                "VH-001", "Test", p, "u", CORRELATION_ID);
            CreateMaintenanceRequest201Response dto = mapper.toCreateResponse(req);
            assertThat(dto.getPriority().name()).isEqualTo(p.name());
        }
    }

    @Test
    void toCreateResponse_statusEnum_mapsCorrectly() {
        MaintenanceRequest req = MaintenanceRequest.create(
            "VH-001", "Test", Priority.LOW, "u", CORRELATION_ID);
        CreateMaintenanceRequest201Response dto = mapper.toCreateResponse(req);
        assertThat(dto.getStatus().name()).isEqualTo(RequestStatus.CREATED.name());
    }

    @Test
    void toInspectionItem_mapsNestedMoneyFields() {
        UUID requestId = UUID.randomUUID();
        InspectionReport report = new InspectionReport(
            UUID.randomUUID(), requestId,
            "Brake pads worn",
            Money.of(INSPECTION_COST, "USD"),
            INSPECTION_DURATION,
            List.of("https://example.com/photo.jpg"),
            Instant.now(),
            "provider-1"
        );

        ListInspections200ResponseInner dto = mapper.toInspectionItem(report);

        assertThat(dto.getReportId()).isEqualTo(report.reportId());
        assertThat(dto.getRequestId()).isEqualTo(requestId);
        assertThat(dto.getFindings()).isEqualTo("Brake pads worn");
        assertThat(dto.getEstimatedCost()).isNotNull();
        assertThat(dto.getEstimatedCost().getAmount()).isEqualTo(INSPECTION_COST.doubleValue());
        assertThat(dto.getEstimatedCost().getCurrency()).isEqualTo("USD");
        assertThat(dto.getEstimatedDurationDays()).isEqualTo(INSPECTION_DURATION);
        assertThat(dto.getSubmittedBy()).isEqualTo("provider-1");
        assertThat(dto.getAttachments()).hasSize(1);
    }

    @Test
    void toDecisionItem_mapsOutcomeEnum() {
        UUID requestId = UUID.randomUUID();
        Decision decision = new Decision(
            UUID.randomUUID(), requestId,
            DecisionOutcome.REJECTED, "Cost too high", "coord-1", Instant.now());

        ListDecisions200ResponseInner dto = mapper.toDecisionItem(decision);

        assertThat(dto.getDecisionId()).isEqualTo(decision.decisionId());
        assertThat(dto.getRequestId()).isEqualTo(requestId);
        assertThat(dto.getOutcome().name()).isEqualTo("REJECTED");
        assertThat(dto.getRemarks()).isEqualTo("Cost too high");
        assertThat(dto.getDecidedBy()).isEqualTo("coord-1");
        assertThat(dto.getDecidedAt()).isNotNull();
    }

    @Test
    void toProviderItem_mapsServiceProvider() {
        UUID providerId = UUID.randomUUID();
        ServiceProvider provider = new ServiceProvider(
            providerId, "AutoFix Ltd", "autofix@example.com", "+1-555-0100", true);

        ListServiceProviders200ResponseInner dto = mapper.toProviderItem(provider);

        assertThat(dto.getProviderId()).isEqualTo(providerId);
        assertThat(dto.getName()).isEqualTo("AutoFix Ltd");
        assertThat(dto.getContactEmail()).isEqualTo("autofix@example.com");
        assertThat(dto.getPhone()).isEqualTo("+1-555-0100");
        assertThat(dto.getActive()).isTrue();
    }

    @Test
    void toEventItem_mapsEventSummaryFields() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant now = Instant.now();
        EventSummary summary = new EventSummary(
            eventId, "maintenance.request.created", now, correlationId, "{}");

        PagedEventDtoContentInner dto = mapper.toEventItem(summary);

        assertThat(dto.getEventId()).isEqualTo(eventId);
        assertThat(dto.getEventType().getValue()).isEqualTo("maintenance.request.created");
        assertThat(dto.getTimestamp()).isNotNull();
        assertThat(dto.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void toOffsetDateTime_convertsInstantToUTC() {
        Instant instant = Instant.parse("2026-05-29T12:00:00Z");
        var result = mapper.toOffsetDateTime(instant);
        assertThat(result).isNotNull();
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(result.toInstant()).isEqualTo(instant);
    }

    @Test
    void toOffsetDateTime_returnsNullForNullInstant() {
        assertThat(mapper.toOffsetDateTime((Instant) null)).isNull();
    }

    @Test
    void toUri_convertsValidString() {
        var uri = mapper.toUri("https://example.com/photo.jpg");
        assertThat(uri).isNotNull();
        assertThat(uri.toString()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void toUri_returnsNullForNull() {
        assertThat(mapper.toUri(null)).isNull();
    }

    @Test
    void toUri_returnsNullForBlankString() {
        assertThat(mapper.toUri("   ")).isNull();
    }

    @Test
    void toRequestDetailDto_assemblesAllParts() {
        UUID requestId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-002", "Tyre swap", Priority.MEDIUM, "coord-2", CORRELATION_ID);

        InspectionReport report = new InspectionReport(
            UUID.randomUUID(), requestId, "Worn tyres",
            Money.of(SIMPLE_COST), 2, List.of(), Instant.now(), "prov-1");

        Decision decision = new Decision(
            UUID.randomUUID(), requestId, DecisionOutcome.APPROVED, null, "coord-2", Instant.now());

        RequestDetailDto dto = mapper.toRequestDetailDto(
            request, List.of(report), List.of(decision), null);

        assertThat(dto.getVehicleId()).isEqualTo("VH-002");
        assertThat(dto.getDescription()).isEqualTo("Tyre swap");
        assertThat(dto.getPriority().name()).isEqualTo("MEDIUM");
        assertThat(dto.getStatus().name()).isEqualTo("CREATED");
        assertThat(dto.getInspections()).hasSize(1);
        assertThat(dto.getDecisions()).hasSize(1);
        assertThat(dto.getAssignedProvider()).isNull();
    }

    @Test
    void toRequestDetailDto_includesProvider_whenPresent() {
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-003", "Oil", Priority.LOW, "coord-3", CORRELATION_ID);
        ServiceProvider provider = new ServiceProvider(
            UUID.randomUUID(), "FixIt", "fixit@example.com", "555-2000", true);

        RequestDetailDto dto = mapper.toRequestDetailDto(
            request, List.of(), List.of(), provider);

        assertThat(dto.getAssignedProvider()).isNotNull();
        assertThat(dto.getAssignedProvider().getName()).isEqualTo("FixIt");
    }
}
