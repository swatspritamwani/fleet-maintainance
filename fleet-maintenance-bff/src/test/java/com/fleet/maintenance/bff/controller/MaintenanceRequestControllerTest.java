package com.fleet.maintenance.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.maintenance.application.service.AssignProviderService;
import com.fleet.maintenance.application.service.CreateMaintenanceRequestService;
import com.fleet.maintenance.application.service.MakeDecisionService;
import com.fleet.maintenance.application.service.RequestQueryService;
import com.fleet.maintenance.application.service.SubmitInspectionService;
import com.fleet.maintenance.bff.config.SecurityConfig;
import com.fleet.maintenance.bff.mapper.BffMapper;
import com.fleet.maintenance.domain.exception.IllegalStateTransitionException;
import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.Priority;
import com.fleet.maintenance.domain.valueobject.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaintenanceRequestController.class)
@Import(SecurityConfig.class)
class MaintenanceRequestControllerTest {

    private static final int DURATION_DAYS = 3;
    private static final int SAMPLE_COST = 500;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateMaintenanceRequestService createService;

    @MockBean
    private AssignProviderService assignService;

    @MockBean
    private SubmitInspectionService submitInspectionService;

    @MockBean
    private MakeDecisionService makeDecisionService;

    @MockBean
    private RequestQueryService queryService;

    @MockBean
    private BffMapper mapper;

    @Test
    void createMaintenanceRequest_returns201() throws Exception {
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-001", "Engine noise", Priority.HIGH, "coord-1", correlationId);

        when(createService.create(any())).thenReturn(request);
        com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response response =
            new com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response();
        response.setRequestId(request.getRequestId());
        response.setVehicleId("VH-001");
        when(mapper.toCreateResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/maintenance-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"vehicleId":"VH-001","description":"Engine noise","priority":"HIGH"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.vehicleId").value("VH-001"));
    }

    @Test
    void createMaintenanceRequest_returns400OnMissingField() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listMaintenanceRequests_returns200() throws Exception {
        when(queryService.listByStatus(any())).thenReturn(List.of());
        when(mapper.toRequestDtos(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maintenance-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getMaintenanceRequest_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-002", "Oil change", Priority.LOW, "coord-1", correlationId);

        when(queryService.getById(requestId)).thenReturn(request);
        when(queryService.getInspections(requestId)).thenReturn(List.of());
        when(queryService.getDecisions(requestId)).thenReturn(List.of());
        when(queryService.getProvider(any())).thenReturn(Optional.empty());
        com.fleet.maintenance.bff.dto.RequestDetailDto detail = new com.fleet.maintenance.bff.dto.RequestDetailDto();
        detail.setRequestId(requestId);
        when(mapper.toRequestDetailDto(any(), any(), any(), any())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}", requestId))
            .andExpect(status().isOk());
    }

    @Test
    void getMaintenanceRequest_returns404WhenNotFound() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.getById(requestId)).thenThrow(new NotFoundException("not found"));

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}", requestId))
            .andExpect(status().isNotFound());
    }

    @Test
    void assignServiceProvider_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        MaintenanceRequest request = MaintenanceRequest.create(
            "VH-003", "Tyre", Priority.MEDIUM, "coord-1", correlationId);

        when(assignService.assign(any())).thenReturn(request);
        com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response response =
            new com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response();
        response.setRequestId(request.getRequestId());
        when(mapper.toCreateResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/assignments", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + providerId + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void submitInspection_returns201() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        InspectionReport report = new InspectionReport(
            reportId, requestId, "Findings", Money.of(BigDecimal.valueOf(SAMPLE_COST)), DURATION_DAYS,
            List.of(), Instant.now(), "prov-1");

        when(submitInspectionService.submit(any())).thenReturn(report);
        com.fleet.maintenance.bff.dto.ListInspections200ResponseInner item =
            new com.fleet.maintenance.bff.dto.ListInspections200ResponseInner();
        item.setReportId(reportId);
        when(mapper.toInspectionItem(any())).thenReturn(item);

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/inspections", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"findings":"Findings","estimatedCost":500.0,"estimatedDurationDays":3}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void submitDecision_returns201() throws Exception {
        UUID requestId = UUID.randomUUID();
        Decision decision = new Decision(UUID.randomUUID(), requestId,
            DecisionOutcome.REJECTED, "Cost too high", "coord-1", Instant.now());

        when(makeDecisionService.decide(any())).thenReturn(decision);
        com.fleet.maintenance.bff.dto.ListDecisions200ResponseInner item =
            new com.fleet.maintenance.bff.dto.ListDecisions200ResponseInner();
        item.setDecisionId(decision.decisionId());
        when(mapper.toDecisionItem(any())).thenReturn(item);
        when(mapper.toDomain(any(com.fleet.maintenance.bff.dto.DecisionOutcome.class)))
            .thenReturn(DecisionOutcome.REJECTED);

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REJECTED\",\"remarks\":\"Cost too high\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void submitDecision_returns409OnStateConflict() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(makeDecisionService.decide(any()))
            .thenThrow(new IllegalStateTransitionException("Already decided"));
        when(mapper.toDomain(any(com.fleet.maintenance.bff.dto.DecisionOutcome.class)))
            .thenReturn(DecisionOutcome.APPROVED);

        mockMvc.perform(post("/api/v1/maintenance-requests/{id}/decisions", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"APPROVED\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void listInspections_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.getInspections(requestId)).thenReturn(List.of());
        when(mapper.toInspectionItems(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}/inspections", requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listDecisions_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.getDecisions(requestId)).thenReturn(List.of());
        when(mapper.toDecisionItems(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maintenance-requests/{id}/decisions", requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
