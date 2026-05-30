package com.fleet.maintenance.application.service;

import com.fleet.maintenance.application.dto.MakeDecisionCommand;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.port.InspectionReportRepository;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MakeDecisionService {

    private final MaintenanceRequestRepository requestRepository;
    private final InspectionReportRepository inspectionReportRepository;

    public MakeDecisionService(
            MaintenanceRequestRepository requestRepository,
            InspectionReportRepository inspectionReportRepository) {
        this.requestRepository = requestRepository;
        this.inspectionReportRepository = inspectionReportRepository;
    }

    public Decision decide(MakeDecisionCommand command) {
        MaintenanceRequest request = requestRepository.findById(command.requestId())
            .orElseThrow(() -> new NotFoundException("Request not found: " + command.requestId()));

        Optional<Decision> result = switch (command.outcome()) {
            case APPROVED -> handleApprove(request, command);
            case REJECTED -> request.reject(command.decidedBy(), command.remarks(), command.correlationId());
            case INFO_REQUESTED -> request.requestInfo(command.decidedBy(), command.remarks(), command.correlationId());
        };

        if (result.isEmpty()) {
            throw new com.fleet.maintenance.domain.exception.IllegalStateTransitionException(
                "Request " + command.requestId() + " is already in terminal state for outcome: " + command.outcome());
        }

        Decision decision = result.get();
        List<DomainEvent> events = request.pullDomainEvents();
        requestRepository.saveWithDecisionAndEvents(request, decision, events);
        return decision;
    }

    private Optional<Decision> handleApprove(MaintenanceRequest request, MakeDecisionCommand command) {
        InspectionReport report = inspectionReportRepository
            .findLatestByRequestId(command.requestId())
            .orElseThrow(() -> new NotFoundException(
                "No inspection report for request: " + command.requestId()));
        return request.approve(
            command.decidedBy(),
            report.estimatedCost(),
            report.estimatedDurationDays(),
            command.correlationId()
        );
    }
}
