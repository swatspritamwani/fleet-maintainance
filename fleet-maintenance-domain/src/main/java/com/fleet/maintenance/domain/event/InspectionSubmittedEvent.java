package com.fleet.maintenance.domain.event;

import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.util.UUID;

public record InspectionSubmittedEvent(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    UUID requestId,
    UUID reportId,
    String submittedBy
) implements DomainEvent {

    public static final String EVENT_TYPE = "maintenance.inspection.submitted";

    public static InspectionSubmittedEvent of(
            MaintenanceRequest request,
            InspectionReport report,
            UUID correlationId) {
        return new InspectionSubmittedEvent(
            UUID.randomUUID(),
            EVENT_TYPE,
            Instant.now(),
            correlationId,
            request.getRequestId(),
            report.reportId(),
            report.submittedBy()
        );
    }
}
