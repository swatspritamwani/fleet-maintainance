package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.RequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRequestRepository {

    void saveWithEvents(MaintenanceRequest request, List<DomainEvent> events);

    void saveWithInspectionAndEvents(
            MaintenanceRequest request, InspectionReport report, List<DomainEvent> events);

    void saveWithDecisionAndEvents(
            MaintenanceRequest request, Decision decision, List<DomainEvent> events);

    Optional<MaintenanceRequest> findById(UUID requestId);

    List<MaintenanceRequest> findByStatus(RequestStatus status);

    List<MaintenanceRequest> findAll();
}
