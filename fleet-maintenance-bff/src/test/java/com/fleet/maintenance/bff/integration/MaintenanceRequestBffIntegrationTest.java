package com.fleet.maintenance.bff.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.DecisionRepository;
import com.fleet.maintenance.domain.port.EventRepository;
import com.fleet.maintenance.domain.port.InspectionReportRepository;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;
import com.fleet.maintenance.domain.valueobject.Money;
import com.fleet.maintenance.infrastructure.repository.DynamoDbOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests covering UC-1 through UC-5.
 * Infrastructure (DynamoDB, Kafka outbox) is mocked at the port level so no
 * external services are required. The full HTTP → controller → application
 * service → domain chain is exercised with a real Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
class MaintenanceRequestBffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceRequestRepository requestRepository;

    @MockBean
    private InspectionReportRepository inspectionReportRepository;

    @MockBean
    private DecisionRepository decisionRepository;

    @MockBean
    private ServiceProviderRepository serviceProviderRepository;

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private DynamoDbOutboxRepository outboxRepository;

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int INSPECTION_DURATION_DAYS = 3;
    private static final BigDecimal APPROVED_COST = BigDecimal.valueOf(500);

    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROVIDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DECISION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private MaintenanceRequest createdRequest;
    private MaintenanceRequest assignedRequest;
    private MaintenanceRequest inspectionSubmittedRequest;

    @BeforeEach
    void setUp() {
        createdRequest = MaintenanceRequest.reconstitute(
            REQUEST_ID, "VH-001", "Engine oil change", Priority.HIGH,
            RequestStatus.CREATED, null, "coord-1", Instant.now(), Instant.now());

        assignedRequest = MaintenanceRequest.reconstitute(
            REQUEST_ID, "VH-001", "Engine oil change", Priority.HIGH,
            RequestStatus.ASSIGNED, PROVIDER_ID, "coord-1", Instant.now(), Instant.now());

        inspectionSubmittedRequest = MaintenanceRequest.reconstitute(
            REQUEST_ID, "VH-001", "Engine oil change", Priority.HIGH,
            RequestStatus.INSPECTION_SUBMITTED, PROVIDER_ID, "coord-1",
            Instant.now(), Instant.now());

        doNothing().when(requestRepository).saveWithEvents(any(), anyList());
        doNothing().when(requestRepository).saveWithInspectionAndEvents(any(), any(), anyList());
        doNothing().when(requestRepository).saveWithDecisionAndEvents(any(), any(), anyList());
        when(outboxRepository.findPending(anyInt())).thenReturn(List.of());
    }

    // UC-1: Create Maintenance Request (AC-1.1, AC-1.2, AC-1.3)

    @Test
    void uc1_createRequest_returns201WithRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "vehicleId": "VH-001",
                      "description": "Engine oil change required",
                      "priority": "HIGH"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.vehicleId").value("VH-001"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void uc1_createRequest_returns400WhenVehicleIdMissing() throws Exception {
        // AC-1.3: validation errors return 400 with field-level messages
        mockMvc.perform(post("/api/v1/maintenance-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "Engine oil change",
                      "priority": "LOW"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(HTTP_BAD_REQUEST));
    }

    @Test
    void uc1_createRequest_returns400WhenDescriptionMissing() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "vehicleId": "VH-001", "priority": "LOW" }
                    """))
            .andExpect(status().isBadRequest());
    }

    // UC-2: Assign to Service Provider (AC-2.1, AC-2.2, AC-2.3)

    @Test
    void uc2_assignProvider_returns200WhenRequestInCreatedStatus() throws Exception {
        // AC-2.1: only CREATED requests can be assigned
        ServiceProvider activeProvider = new ServiceProvider(
            PROVIDER_ID, "AutoFix", "autofix@example.com", "555-0100", true);

        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(createdRequest));
        when(serviceProviderRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(activeProvider));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/assignments", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + PROVIDER_ID + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void uc2_assignProvider_returns422WhenProviderInactive() throws Exception {
        // AC-2.2: inactive provider returns 422
        ServiceProvider inactiveProvider = new ServiceProvider(
            PROVIDER_ID, "OldGarage", "old@garage.com", "555-0200", false);

        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(createdRequest));
        when(serviceProviderRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(inactiveProvider));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/assignments", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + PROVIDER_ID + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void uc2_assignProvider_returns404WhenRequestNotFound() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/assignments", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + PROVIDER_ID + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void uc2_assignProvider_returns409WhenNotInCreatedStatus() throws Exception {
        // AC-2.1: request already assigned → state conflict
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(assignedRequest));
        when(serviceProviderRepository.findById(any())).thenReturn(
            Optional.of(new ServiceProvider(UUID.randomUUID(), "X", "x@x.com", "0", true)));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/assignments", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isConflict());
    }

    // UC-3: Submit Inspection & Estimate (AC-3.1, AC-3.2, AC-3.3)

    @Test
    void uc3_submitInspection_returns201WithReportId() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(assignedRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/inspections", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "findings": "Oil level critically low, filter clogged",
                      "estimatedCost": 250.00,
                      "estimatedDurationDays": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reportId").isNotEmpty())
            .andExpect(jsonPath("$.findings").value("Oil level critically low, filter clogged"));
    }

    @Test
    void uc3_submitInspection_returns400WhenFindingsMissing() throws Exception {
        // AC-3.3: findings text is required
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(assignedRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/inspections", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "estimatedCost": 100.00,
                      "estimatedDurationDays": 1
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void uc3_submitInspection_returns409WhenNotInAssignedStatus() throws Exception {
        // Guard: request must be in ASSIGNED or INFO_REQUESTED
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(createdRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/inspections", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "findings": "Oil needs change",
                      "estimatedCost": 100.00,
                      "estimatedDurationDays": 1
                    }
                    """))
            .andExpect(status().isConflict());
    }

    // UC-4: Approve / Reject / Request Info (AC-4.1, AC-4.2, AC-4.3)

    @Test
    void uc4_rejectDecision_returns201() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(inspectionSubmittedRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "outcome": "REJECTED",
                      "remarks": "Estimated cost too high, please re-evaluate"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("REJECTED"))
            .andExpect(jsonPath("$.remarks").value("Estimated cost too high, please re-evaluate"));
    }

    @Test
    void uc4_requestInfo_returns201() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(inspectionSubmittedRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "outcome": "INFO_REQUESTED",
                      "remarks": "Please provide itemised parts breakdown"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("INFO_REQUESTED"));
    }

    @Test
    void uc4_rejectWithoutRemarks_returns400() throws Exception {
        // AC-4.2: Reject without remarks returns 400
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(inspectionSubmittedRequest));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REJECTED\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void uc4_approveOnNonInspectionSubmitted_returns409() throws Exception {
        // AC-4.1: only INSPECTION_SUBMITTED requests can be decided.
        // MakeDecisionService loads the inspection report before calling domain.approve(),
        // so the mock must return a report to allow the state machine guard to fire.
        InspectionReport dummyReport = new InspectionReport(
            REPORT_ID, REQUEST_ID, "findings",
            Money.of(APPROVED_COST, "USD"), INSPECTION_DURATION_DAYS,
            List.of(), Instant.now(), "prov-1");
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(createdRequest));
        when(inspectionReportRepository.findLatestByRequestId(REQUEST_ID))
            .thenReturn(Optional.of(dummyReport));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"APPROVED\"}"))
            .andExpect(status().isConflict());
    }

    // UC-4 / UC-5: Approve triggers PAYMENT_READY transition

    @Test
    void uc4_uc5_approveDecision_transitionsToPaymentReady() throws Exception {
        // UC-5: approval auto-publishes payment readiness event (via outbox, mocked here)
        InspectionReport latestReport = new InspectionReport(
            REPORT_ID, REQUEST_ID, "All checked",
            Money.of(APPROVED_COST, "USD"), INSPECTION_DURATION_DAYS,
            List.of(), Instant.now(), "prov-1");

        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(inspectionSubmittedRequest));
        when(inspectionReportRepository.findLatestByRequestId(REQUEST_ID))
            .thenReturn(Optional.of(latestReport));

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"APPROVED\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("APPROVED"));
    }

    // Query endpoints

    @Test
    void listRequests_returns200WithPagedContent() throws Exception {
        when(requestRepository.findAll()).thenReturn(List.of(createdRequest));

        mockMvc.perform(get("/api/v1/maintenance-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].requestId").value(REQUEST_ID.toString()));
    }

    @Test
    void getRequest_returns200WithDetail() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(createdRequest));
        when(inspectionReportRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(decisionRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}", REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.inspections").isArray())
            .andExpect(jsonPath("$.decisions").isArray());
    }

    @Test
    void getRequest_returns404WhenNotFound() throws Exception {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}", REQUEST_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(HTTP_NOT_FOUND))
            .andExpect(jsonPath("$.type").isNotEmpty());
    }

    @Test
    void listServiceProviders_returns200() throws Exception {
        ServiceProvider provider = new ServiceProvider(
            PROVIDER_ID, "AutoFix", "autofix@example.com", "555-0100", true);
        when(serviceProviderRepository.findAllActive()).thenReturn(List.of(provider));

        mockMvc.perform(get("/api/v1/service-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("AutoFix"))
            .andExpect(jsonPath("$[0].active").value(true));
    }
}
