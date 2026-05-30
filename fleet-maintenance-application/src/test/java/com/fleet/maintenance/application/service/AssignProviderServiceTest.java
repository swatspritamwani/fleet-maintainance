package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.AssignProviderCommand;
import com.fleet.maintenance.domain.exception.DomainValidationException;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignProviderServiceTest {

    @Mock
    private MaintenanceRequestRepository requestRepository;

    @Mock
    private ServiceProviderRepository providerRepository;

    private AssignProviderService service;

    private UUID requestId;
    private UUID providerId;
    private UUID correlationId;
    private MaintenanceRequest request;
    private ServiceProvider activeProvider;

    @BeforeEach
    void setUp() {
        service = new AssignProviderService(requestRepository, providerRepository);
        requestId = UUID.randomUUID();
        providerId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        request = MaintenanceRequest.create("VH-001", "Test", Priority.MEDIUM, "coord-1", correlationId);
        request.pullDomainEvents();
        activeProvider = new ServiceProvider(providerId, "AutoFix", "fix@example.com", "555-0100", true);
    }

    @Test
    void assignsProviderToRequest() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(activeProvider));

        MaintenanceRequest result = service.assign(new AssignProviderCommand(requestId, providerId, correlationId));

        assertThat(result.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(result.getAssignedProviderId()).isEqualTo(providerId);
        verify(requestRepository).saveWithEvents(any(MaintenanceRequest.class), anyList());
    }

    @Test
    void throwsNotFoundWhenRequestMissing() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.assign(new AssignProviderCommand(requestId, providerId, correlationId)))
            .isInstanceOf(NotFoundException.class);
        verify(requestRepository, never()).saveWithEvents(any(), any());
    }

    @Test
    void throwsNotFoundWhenProviderMissing() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.assign(new AssignProviderCommand(requestId, providerId, correlationId)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsWhenProviderInactive() {
        ServiceProvider inactiveProvider =
            new ServiceProvider(providerId, "AutoFix", "fix@example.com", "555-0100", false);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(inactiveProvider));

        assertThatThrownBy(() ->
            service.assign(new AssignProviderCommand(requestId, providerId, correlationId)))
            .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void idempotentAssignDoesNotSave() {
        request.assign(providerId, correlationId);
        request.pullDomainEvents();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(activeProvider));

        service.assign(new AssignProviderCommand(requestId, providerId, correlationId));

        verify(requestRepository, never()).saveWithEvents(any(), any());
    }
}
