package com.fleet.maintenance.domain.model;

import com.fleet.maintenance.domain.event.DecisionApprovedEvent;
import com.fleet.maintenance.domain.event.DecisionInfoRequestedEvent;
import com.fleet.maintenance.domain.event.DecisionRejectedEvent;
import com.fleet.maintenance.domain.event.DomainEvent;
import com.fleet.maintenance.domain.event.InspectionSubmittedEvent;
import com.fleet.maintenance.domain.event.PaymentReadinessEvent;
import com.fleet.maintenance.domain.event.RequestAssignedEvent;
import com.fleet.maintenance.domain.event.RequestCreatedEvent;
import com.fleet.maintenance.domain.exception.DomainValidationException;
import com.fleet.maintenance.domain.exception.IllegalStateTransitionException;
import com.fleet.maintenance.domain.valueobject.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class MaintenanceRequest {

    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final UUID requestId;
    private final String vehicleId;
    private final String description;
    private final Priority priority;
    private RequestStatus status;
    private UUID assignedProviderId;
    private final String createdBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private MaintenanceRequest(UUID requestId, String vehicleId, String description,
            Priority priority, String createdBy, Instant createdAt) {
        this.requestId = requestId;
        this.vehicleId = vehicleId;
        this.description = description;
        this.priority = priority;
        this.status = RequestStatus.CREATED;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static MaintenanceRequest reconstitute(UUID requestId, String vehicleId,
            String description, Priority priority, RequestStatus status,
            UUID assignedProviderId, String createdBy,
            Instant createdAt, Instant updatedAt) {
        MaintenanceRequest request = new MaintenanceRequest(
            requestId, vehicleId, description, priority, createdBy, createdAt);
        request.status = status;
        request.assignedProviderId = assignedProviderId;
        request.updatedAt = updatedAt;
        return request;
    }

    public static MaintenanceRequest create(String vehicleId, String description,
            Priority priority, String createdBy, UUID correlationId) {
        validateCreateInput(vehicleId, description, priority, createdBy);
        Instant now = Instant.now();
        MaintenanceRequest request = new MaintenanceRequest(
            UUID.randomUUID(), vehicleId, description, priority, createdBy, now);
        request.registerEvent(RequestCreatedEvent.of(request, correlationId));
        return request;
    }

    public void assign(UUID providerId, UUID correlationId) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (status == RequestStatus.ASSIGNED && providerId.equals(assignedProviderId)) {
            return;
        }
        requireStatus(RequestStatus.CREATED, "assign");
        this.assignedProviderId = providerId;
        this.status = RequestStatus.ASSIGNED;
        this.updatedAt = Instant.now();
        registerEvent(RequestAssignedEvent.of(this, correlationId));
    }

    public Optional<InspectionReport> submitInspection(String findings, Money estimatedCost,
            int estimatedDurationDays, List<String> attachments,
            String submittedBy, UUID correlationId) {
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        Objects.requireNonNull(submittedBy, "submittedBy must not be null");
        if (status == RequestStatus.INSPECTION_SUBMITTED) {
            return Optional.empty();
        }
        if (status != RequestStatus.ASSIGNED && status != RequestStatus.INFO_REQUESTED) {
            throw new IllegalStateTransitionException(
                "Cannot submit inspection: request is in status " + status);
        }
        InspectionReport report = new InspectionReport(
            UUID.randomUUID(), requestId, findings, estimatedCost,
            estimatedDurationDays,
            attachments != null ? List.copyOf(attachments) : List.of(),
            Instant.now(), submittedBy);
        this.status = RequestStatus.INSPECTION_SUBMITTED;
        this.updatedAt = Instant.now();
        registerEvent(InspectionSubmittedEvent.of(this, report, correlationId));
        return Optional.of(report);
    }

    public Optional<Decision> approve(String decidedBy, Money approvedCost,
            int estimatedDurationDays, UUID correlationId) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        Objects.requireNonNull(approvedCost, "approvedCost must not be null");
        if (status == RequestStatus.PAYMENT_READY) {
            return Optional.empty();
        }
        requireStatus(RequestStatus.INSPECTION_SUBMITTED, "approve");
        Instant now = Instant.now();
        Decision decision = new Decision(
            UUID.randomUUID(), requestId, DecisionOutcome.APPROVED, null, decidedBy, now);
        this.status = RequestStatus.APPROVED;
        this.updatedAt = now;
        registerEvent(DecisionApprovedEvent.of(this, decision, correlationId));
        this.status = RequestStatus.PAYMENT_READY;
        this.updatedAt = Instant.now();
        registerEvent(
            PaymentReadinessEvent.of(this, approvedCost, estimatedDurationDays, decidedBy, correlationId));
        return Optional.of(decision);
    }

    public Optional<Decision> reject(String decidedBy, String remarks, UUID correlationId) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        if (remarks == null || remarks.isBlank()) {
            throw new DomainValidationException("remarks required for rejection");
        }
        if (status == RequestStatus.REJECTED) {
            return Optional.empty();
        }
        requireStatus(RequestStatus.INSPECTION_SUBMITTED, "reject");
        Instant now = Instant.now();
        Decision decision = new Decision(
            UUID.randomUUID(), requestId, DecisionOutcome.REJECTED, remarks, decidedBy, now);
        this.status = RequestStatus.REJECTED;
        this.updatedAt = now;
        registerEvent(DecisionRejectedEvent.of(this, decision, correlationId));
        return Optional.of(decision);
    }

    public Optional<Decision> requestInfo(String decidedBy, String remarks, UUID correlationId) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        if (remarks == null || remarks.isBlank()) {
            throw new DomainValidationException("remarks required for info request");
        }
        if (status == RequestStatus.INFO_REQUESTED) {
            return Optional.empty();
        }
        requireStatus(RequestStatus.INSPECTION_SUBMITTED, "requestInfo");
        Instant now = Instant.now();
        Decision decision = new Decision(
            UUID.randomUUID(), requestId, DecisionOutcome.INFO_REQUESTED, remarks, decidedBy, now);
        this.status = RequestStatus.INFO_REQUESTED;
        this.updatedAt = now;
        registerEvent(DecisionInfoRequestedEvent.of(this, decision, correlationId));
        return Optional.of(decision);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    private void requireStatus(RequestStatus required, String operation) {
        if (status != required) {
            throw new IllegalStateTransitionException(
                "Cannot " + operation + ": request is in status "
                + status + " but required " + required);
        }
    }

    private static void validateCreateInput(String vehicleId, String description,
            Priority priority, String createdBy) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new DomainValidationException("vehicleId is required");
        }
        if (description == null || description.isBlank()) {
            throw new DomainValidationException("description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainValidationException(
                "description exceeds " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        Objects.requireNonNull(priority, "priority is required");
        if (createdBy == null || createdBy.isBlank()) {
            throw new DomainValidationException("createdBy is required");
        }
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public UUID getAssignedProviderId() {
        return assignedProviderId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
