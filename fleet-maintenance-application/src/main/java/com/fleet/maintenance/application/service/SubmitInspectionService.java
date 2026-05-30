package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.SubmitInspectionCommand;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SubmitInspectionService {

    private final MaintenanceRequestRepository requestRepository;

    public SubmitInspectionService(MaintenanceRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public InspectionReport submit(SubmitInspectionCommand command) {
        MaintenanceRequest request = requestRepository.findById(command.requestId())
            .orElseThrow(() -> new NotFoundException("Request not found: " + command.requestId()));

        Optional<InspectionReport> reportOpt = request.submitInspection(
            command.findings(),
            command.estimatedCost(),
            command.estimatedDurationDays(),
            command.attachments(),
            command.submittedBy(),
            command.correlationId()
        );

        if (reportOpt.isEmpty()) {
            throw new com.fleet.maintenance.domain.exception.IllegalStateTransitionException(
                "Inspection already submitted for request: " + command.requestId());
        }

        InspectionReport report = reportOpt.get();
        List<DomainEvent> events = request.pullDomainEvents();
        requestRepository.saveWithInspectionAndEvents(request, report, events);
        return report;
    }
}
