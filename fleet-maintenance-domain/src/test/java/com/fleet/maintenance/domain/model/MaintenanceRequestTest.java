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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceRequestTest {

    private static final String VEHICLE_ID = "VH-001";
    private static final String DESCRIPTION = "Engine oil needs replacement";
    private static final String CREATED_BY = "coordinator-1";
    private static final String PROVIDER_ID_STR = "provider-1";
    private static final String FINDINGS = "Oil level critically low";
    private static final String REMARKS = "Need more detail on parts costs";
    private static final BigDecimal COST_AMOUNT = BigDecimal.ONE;
    private static final int DURATION_DAYS = 1;
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private UUID providerId;
    private Money estimatedCost;
    private MaintenanceRequest request;

    @BeforeEach
    void setUp() {
        providerId = UUID.randomUUID();
        estimatedCost = Money.of(COST_AMOUNT);
        request = MaintenanceRequest.create(VEHICLE_ID, DESCRIPTION, Priority.MEDIUM, CREATED_BY, CORRELATION_ID);
        request.pullDomainEvents();
    }

    @Nested
    class Create {
        @Test
        void createsRequestInCreatedStatus() {
            MaintenanceRequest req = MaintenanceRequest.create(
                VEHICLE_ID, DESCRIPTION, Priority.HIGH, CREATED_BY, CORRELATION_ID);
            assertThat(req.getStatus()).isEqualTo(RequestStatus.CREATED);
            assertThat(req.getVehicleId()).isEqualTo(VEHICLE_ID);
            assertThat(req.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(req.getCreatedBy()).isEqualTo(CREATED_BY);
        }

        @Test
        void registersCreatedEvent() {
            MaintenanceRequest req = MaintenanceRequest.create(
                VEHICLE_ID, DESCRIPTION, Priority.LOW, CREATED_BY, CORRELATION_ID);
            List<DomainEvent> events = req.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(RequestCreatedEvent.class);
        }

        @Test
        void throwsWhenVehicleIdBlank() {
            assertThatThrownBy(() ->
                MaintenanceRequest.create("", DESCRIPTION, Priority.LOW, CREATED_BY, CORRELATION_ID))
                .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void throwsWhenDescriptionBlank() {
            assertThatThrownBy(() ->
                MaintenanceRequest.create(VEHICLE_ID, "  ", Priority.LOW, CREATED_BY, CORRELATION_ID))
                .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void throwsWhenDescriptionTooLong() {
            String tooLong = "x".repeat(MaintenanceRequest.MAX_DESCRIPTION_LENGTH + 1);
            assertThatThrownBy(() ->
                MaintenanceRequest.create(VEHICLE_ID, tooLong, Priority.LOW, CREATED_BY, CORRELATION_ID))
                .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class Assign {
        @Test
        void transitionsFromCreatedToAssigned() {
            request.assign(providerId, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
            assertThat(request.getAssignedProviderId()).isEqualTo(providerId);
        }

        @Test
        void registersAssignedEvent() {
            request.assign(providerId, CORRELATION_ID);
            List<DomainEvent> events = request.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(RequestAssignedEvent.class);
        }

        @Test
        void idempotentWhenAlreadyAssignedWithSameProvider() {
            request.assign(providerId, CORRELATION_ID);
            request.pullDomainEvents();
            request.assign(providerId, CORRELATION_ID);
            assertThat(request.pullDomainEvents()).isEmpty();
        }

        @Test
        void throwsWhenAlreadyAssignedToDifferentProvider() {
            request.assign(providerId, CORRELATION_ID);
            UUID otherId = UUID.randomUUID();
            assertThatThrownBy(() -> request.assign(otherId, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void throwsWhenNotInCreatedStatus() {
            request.assign(providerId, CORRELATION_ID);
            request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            assertThatThrownBy(() -> request.assign(UUID.randomUUID(), CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    class SubmitInspection {
        @Test
        void transitionsFromAssignedToInspectionSubmitted() {
            request.assign(providerId, CORRELATION_ID);
            Optional<InspectionReport> report = request.submitInspection(
                FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.INSPECTION_SUBMITTED);
            assertThat(report).isPresent();
        }

        @Test
        void transitionsFromInfoRequestedToInspectionSubmitted() {
            assignAndInspectAndRequestInfo();
            request.pullDomainEvents();
            Optional<InspectionReport> report = request.submitInspection(
                FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.INSPECTION_SUBMITTED);
            assertThat(report).isPresent();
        }

        @Test
        void registersInspectionSubmittedEvent() {
            request.assign(providerId, CORRELATION_ID);
            request.pullDomainEvents();
            request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            List<DomainEvent> events = request.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(InspectionSubmittedEvent.class);
        }

        @Test
        void idempotentWhenAlreadyInspectionSubmitted() {
            request.assign(providerId, CORRELATION_ID);
            request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            request.pullDomainEvents();
            Optional<InspectionReport> result = request.submitInspection(
                FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
            assertThat(result).isEmpty();
            assertThat(request.pullDomainEvents()).isEmpty();
        }

        @Test
        void throwsWhenNotInValidState() {
            assertThatThrownBy(() ->
                request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    class Approve {
        @Test
        void transitionsToPaymentReady() {
            assignAndInspect();
            request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PAYMENT_READY);
        }

        @Test
        void registersTwoEventsApprovedAndPaymentReady() {
            assignAndInspect();
            request.pullDomainEvents();
            request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            List<DomainEvent> events = request.pullDomainEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(DecisionApprovedEvent.class);
            assertThat(events.get(1)).isInstanceOf(PaymentReadinessEvent.class);
        }

        @Test
        void idempotentWhenAlreadyPaymentReady() {
            assignAndInspect();
            request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            request.pullDomainEvents();
            Optional<Decision> result = request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            assertThat(result).isEmpty();
        }

        @Test
        void throwsWhenNotInspectionSubmitted() {
            request.assign(providerId, CORRELATION_ID);
            assertThatThrownBy(() -> request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void throwsWhenDecidedByNull() {
            assignAndInspect();
            assertThatThrownBy(() -> request.approve(null, estimatedCost, DURATION_DAYS, CORRELATION_ID))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Reject {
        @Test
        void transitionsToRejected() {
            assignAndInspect();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        }

        @Test
        void registersRejectedEvent() {
            assignAndInspect();
            request.pullDomainEvents();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            List<DomainEvent> events = request.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(DecisionRejectedEvent.class);
        }

        @Test
        void idempotentWhenAlreadyRejected() {
            assignAndInspect();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            request.pullDomainEvents();
            Optional<Decision> result = request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThat(result).isEmpty();
        }

        @Test
        void throwsWhenRemarksBlank() {
            assignAndInspect();
            assertThatThrownBy(() -> request.reject(CREATED_BY, "  ", CORRELATION_ID))
                .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void throwsWhenNotInspectionSubmitted() {
            assertThatThrownBy(() -> request.reject(CREATED_BY, REMARKS, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    class RequestInfo {
        @Test
        void transitionsToInfoRequested() {
            assignAndInspect();
            request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.INFO_REQUESTED);
        }

        @Test
        void registersInfoRequestedEvent() {
            assignAndInspect();
            request.pullDomainEvents();
            request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID);
            List<DomainEvent> events = request.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(DecisionInfoRequestedEvent.class);
        }

        @Test
        void idempotentWhenAlreadyInfoRequested() {
            assignAndInspect();
            request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID);
            request.pullDomainEvents();
            Optional<Decision> result = request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThat(result).isEmpty();
        }

        @Test
        void throwsWhenRemarksNull() {
            assignAndInspect();
            assertThatThrownBy(() -> request.requestInfo(CREATED_BY, null, CORRELATION_ID))
                .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class TerminalStateProtection {
        @Test
        void rejectOnPaymentReadyThrows() {
            assignAndInspect();
            request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            assertThatThrownBy(() -> request.reject(CREATED_BY, REMARKS, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void requestInfoOnPaymentReadyThrows() {
            assignAndInspect();
            request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID);
            assertThatThrownBy(() -> request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void approveOnRejectedThrows() {
            assignAndInspect();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThatThrownBy(() -> request.approve(CREATED_BY, estimatedCost, DURATION_DAYS, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void assignOnRejectedThrows() {
            assignAndInspect();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThatThrownBy(() -> request.assign(UUID.randomUUID(), CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void submitInspectionOnRejectedThrows() {
            assignAndInspect();
            request.reject(CREATED_BY, REMARKS, CORRELATION_ID);
            assertThatThrownBy(() ->
                request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID))
                .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    class Reconstitute {
        @Test
        void reconstituteRestoresAllFields() {
            UUID reqId = UUID.randomUUID();
            MaintenanceRequest req = MaintenanceRequest.reconstitute(
                reqId, VEHICLE_ID, DESCRIPTION, Priority.CRITICAL,
                RequestStatus.ASSIGNED, providerId, CREATED_BY,
                java.time.Instant.now(), java.time.Instant.now());
            assertThat(req.getRequestId()).isEqualTo(reqId);
            assertThat(req.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
            assertThat(req.getAssignedProviderId()).isEqualTo(providerId);
            assertThat(req.pullDomainEvents()).isEmpty();
        }
    }

    private void assignAndInspect() {
        request.assign(providerId, CORRELATION_ID);
        request.submitInspection(FINDINGS, estimatedCost, DURATION_DAYS, null, PROVIDER_ID_STR, CORRELATION_ID);
        request.pullDomainEvents();
    }

    private void assignAndInspectAndRequestInfo() {
        assignAndInspect();
        request.requestInfo(CREATED_BY, REMARKS, CORRELATION_ID);
    }
}
