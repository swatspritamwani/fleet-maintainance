package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.AssignProviderCommand;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssignProviderService {

    private final MaintenanceRequestRepository requestRepository;
    private final ServiceProviderRepository providerRepository;

    public AssignProviderService(
            MaintenanceRequestRepository requestRepository,
            ServiceProviderRepository providerRepository) {
        this.requestRepository = requestRepository;
        this.providerRepository = providerRepository;
    }

    public MaintenanceRequest assign(AssignProviderCommand command) {
        MaintenanceRequest request = requestRepository.findById(command.requestId())
            .orElseThrow(() -> new NotFoundException("Request not found: " + command.requestId()));

        ServiceProvider provider = providerRepository.findById(command.providerId())
            .orElseThrow(() -> new NotFoundException("Provider not found: " + command.providerId()));

        if (!provider.active()) {
            throw new com.fleet.maintenance.domain.exception.DomainValidationException(
                "Provider is not active: " + command.providerId());
        }

        request.assign(command.providerId(), command.correlationId());
        List<DomainEvent> events = request.pullDomainEvents();
        if (!events.isEmpty()) {
            requestRepository.saveWithEvents(request, events);
        }
        return request;
    }
}
