package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.CreateRequestCommand;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CreateMaintenanceRequestService {

    private final MaintenanceRequestRepository requestRepository;

    public CreateMaintenanceRequestService(MaintenanceRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public MaintenanceRequest create(CreateRequestCommand command) {
        MaintenanceRequest request = MaintenanceRequest.create(
            command.vehicleId(),
            command.description(),
            command.priority(),
            command.createdBy(),
            command.correlationId()
        );
        List<DomainEvent> events = request.pullDomainEvents();
        requestRepository.saveWithEvents(request, events);
        return request;
    }
}
