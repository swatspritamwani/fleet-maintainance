package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.CreateRequestCommand;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateMaintenanceRequestServiceTest {

    @Mock
    private MaintenanceRequestRepository requestRepository;

    private CreateMaintenanceRequestService service;

    @BeforeEach
    void setUp() {
        service = new CreateMaintenanceRequestService(requestRepository);
    }

    @Test
    void createPersistsRequestWithEvent() {
        CreateRequestCommand command = new CreateRequestCommand(
            "VH-001", "Engine oil change", Priority.MEDIUM, "coordinator-1", UUID.randomUUID());

        MaintenanceRequest result = service.create(command);

        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo("VH-001");
        assertThat(result.getPriority()).isEqualTo(Priority.MEDIUM);
        verify(requestRepository).saveWithEvents(any(MaintenanceRequest.class), anyList());
    }

    @Test
    void createSavesExactlyOneEvent() {
        CreateRequestCommand command = new CreateRequestCommand(
            "VH-002", "Brake pad replacement", Priority.HIGH, "coordinator-1", UUID.randomUUID());

        service.create(command);

        ArgumentCaptor<List<DomainEvent>> eventsCaptor = ArgumentCaptor.captor();
        verify(requestRepository).saveWithEvents(any(MaintenanceRequest.class), eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(1);
    }

    @Test
    void createReturnsDomainEventsConsumed() {
        CreateRequestCommand command = new CreateRequestCommand(
            "VH-003", "Tyre rotation", Priority.LOW, "coordinator-1", UUID.randomUUID());

        MaintenanceRequest result = service.create(command);

        assertThat(result.pullDomainEvents()).isEmpty();
    }
}
