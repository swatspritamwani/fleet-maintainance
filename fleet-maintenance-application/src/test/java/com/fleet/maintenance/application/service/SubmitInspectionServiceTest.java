package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.SubmitInspectionCommand;
import com.fleet.maintenance.domain.exception.IllegalStateTransitionException;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.valueobject.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class SubmitInspectionServiceTest {

    @Mock
    private MaintenanceRequestRepository requestRepository;

    private SubmitInspectionService service;

    private UUID requestId;
    private UUID correlationId;
    private MaintenanceRequest assignedRequest;
    private SubmitInspectionCommand validCommand;

    @BeforeEach
    void setUp() {
        service = new SubmitInspectionService(requestRepository);
        requestId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        assignedRequest = MaintenanceRequest.create("VH-001", "Test", Priority.MEDIUM, "coord-1", correlationId);
        assignedRequest.assign(UUID.randomUUID(), correlationId);
        assignedRequest.pullDomainEvents();
        validCommand = new SubmitInspectionCommand(
            requestId, "Oil low", Money.of(BigDecimal.ONE), 1, List.of(), "provider-1", correlationId);
    }

    @Test
    void submitCreatesInspectionReportAndSaves() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(assignedRequest));

        InspectionReport report = service.submit(validCommand);

        assertThat(report).isNotNull();
        assertThat(report.findings()).isEqualTo("Oil low");
        verify(requestRepository).saveWithInspectionAndEvents(
            any(MaintenanceRequest.class), any(InspectionReport.class), anyList());
    }

    @Test
    void throwsWhenRequestNotFound() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(validCommand))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsWhenAlreadyInspectionSubmitted() {
        assignedRequest.submitInspection("findings", Money.of(BigDecimal.ONE), 1, List.of(), "p-1", correlationId);
        assignedRequest.pullDomainEvents();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(assignedRequest));

        assertThatThrownBy(() -> service.submit(validCommand))
            .isInstanceOf(IllegalStateTransitionException.class);
    }
}
