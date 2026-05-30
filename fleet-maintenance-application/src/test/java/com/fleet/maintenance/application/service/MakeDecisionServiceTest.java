package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.MakeDecisionCommand;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.port.InspectionReportRepository;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.valueobject.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakeDecisionServiceTest {

    @Mock
    private MaintenanceRequestRepository requestRepository;

    @Mock
    private InspectionReportRepository inspectionReportRepository;

    private MakeDecisionService service;

    private UUID requestId;
    private UUID correlationId;
    private MaintenanceRequest inspectedRequest;
    private InspectionReport report;

    @BeforeEach
    void setUp() {
        service = new MakeDecisionService(requestRepository, inspectionReportRepository);
        requestId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        inspectedRequest = MaintenanceRequest.create("VH-001", "Test", Priority.HIGH, "coord-1", correlationId);
        inspectedRequest.assign(providerId, correlationId);
        inspectedRequest.submitInspection("findings", Money.of(BigDecimal.ONE), 1, List.of(), "prov-1", correlationId);
        inspectedRequest.pullDomainEvents();
        report = new InspectionReport(
            UUID.randomUUID(), requestId, "Oil low", Money.of(BigDecimal.ONE),
            1, List.of(), Instant.now(), "prov-1");
    }

    @Test
    void approveTransitionsToPaymentReady() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(inspectedRequest));
        when(inspectionReportRepository.findLatestByRequestId(requestId)).thenReturn(Optional.of(report));

        Decision decision = service.decide(
            new MakeDecisionCommand(requestId, DecisionOutcome.APPROVED, null, "coord-1", correlationId));

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.APPROVED);
        verify(requestRepository).saveWithDecisionAndEvents(any(), any(), anyList());
    }

    @Test
    void rejectTransitionsToRejected() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(inspectedRequest));

        Decision decision = service.decide(
            new MakeDecisionCommand(requestId, DecisionOutcome.REJECTED, "Need more info", "coord-1", correlationId));

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.REJECTED);
        assertThat(decision.remarks()).isEqualTo("Need more info");
    }

    @Test
    void requestInfoTransitionsToInfoRequested() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(inspectedRequest));

        Decision decision = service.decide(
            new MakeDecisionCommand(requestId, DecisionOutcome.INFO_REQUESTED, "Clarify parts", "coord-1", correlationId));

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.INFO_REQUESTED);
    }

    @Test
    void throwsWhenRequestNotFound() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(
            new MakeDecisionCommand(requestId, DecisionOutcome.REJECTED, "remarks", "coord-1", correlationId)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsWhenNoInspectionReportForApproval() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(inspectedRequest));
        when(inspectionReportRepository.findLatestByRequestId(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(
            new MakeDecisionCommand(requestId, DecisionOutcome.APPROVED, null, "coord-1", correlationId)))
            .isInstanceOf(NotFoundException.class);
    }
}
