package com.fleet.maintenance.bff.controller;

import com.fleet.maintenance.application.dto.AssignProviderCommand;
import com.fleet.maintenance.application.dto.CreateRequestCommand;
import com.fleet.maintenance.application.dto.MakeDecisionCommand;
import com.fleet.maintenance.application.dto.SubmitInspectionCommand;
import com.fleet.maintenance.application.service.AssignProviderService;
import com.fleet.maintenance.application.service.CreateMaintenanceRequestService;
import com.fleet.maintenance.application.service.MakeDecisionService;
import com.fleet.maintenance.application.service.RequestQueryService;
import com.fleet.maintenance.application.service.SubmitInspectionService;
import com.fleet.maintenance.bff.api.AssignmentsApi;
import com.fleet.maintenance.bff.api.DecisionsApi;
import com.fleet.maintenance.bff.api.InspectionsApi;
import com.fleet.maintenance.bff.api.MaintenanceRequestsApi;
import com.fleet.maintenance.bff.config.CorrelationIdFilter;
import com.fleet.maintenance.bff.dto.AssignProviderDto;
import com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response;
import com.fleet.maintenance.bff.dto.CreateRequestDto;
import com.fleet.maintenance.bff.dto.DecisionRequestDto;
import com.fleet.maintenance.bff.dto.ListDecisions200ResponseInner;
import com.fleet.maintenance.bff.dto.ListInspections200ResponseInner;
import com.fleet.maintenance.bff.dto.PagedRequestDto;
import com.fleet.maintenance.bff.dto.Priority;
import com.fleet.maintenance.bff.dto.RequestDetailDto;
import com.fleet.maintenance.bff.dto.RequestStatus;
import com.fleet.maintenance.bff.mapper.BffMapper;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.DecisionOutcome;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.valueobject.Money;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class MaintenanceRequestController
        implements MaintenanceRequestsApi, AssignmentsApi, InspectionsApi, DecisionsApi {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CreateMaintenanceRequestService createService;
    private final AssignProviderService assignService;
    private final SubmitInspectionService submitInspectionService;
    private final MakeDecisionService makeDecisionService;
    private final RequestQueryService queryService;
    private final BffMapper mapper;
    private final HttpServletRequest httpRequest;

    public MaintenanceRequestController(
            CreateMaintenanceRequestService createService,
            AssignProviderService assignService,
            SubmitInspectionService submitInspectionService,
            MakeDecisionService makeDecisionService,
            RequestQueryService queryService,
            BffMapper mapper,
            HttpServletRequest httpRequest) {
        this.createService = createService;
        this.assignService = assignService;
        this.submitInspectionService = submitInspectionService;
        this.makeDecisionService = makeDecisionService;
        this.queryService = queryService;
        this.mapper = mapper;
        this.httpRequest = httpRequest;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<CreateMaintenanceRequest201Response> createMaintenanceRequest(
            CreateRequestDto body) {
        CreateRequestCommand command = new CreateRequestCommand(
            body.getVehicleId(),
            body.getDescription(),
            com.fleet.maintenance.domain.model.Priority.valueOf(body.getPriority().name()),
            principal(),
            correlationId()
        );
        MaintenanceRequest request = createService.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toCreateResponse(request));
    }

    @Override
    public ResponseEntity<PagedRequestDto> listMaintenanceRequests(
            Integer page, Integer size, String sort,
            RequestStatus status, Priority priority,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo) {
        com.fleet.maintenance.domain.model.RequestStatus domainStatus =
            status != null
                ? com.fleet.maintenance.domain.model.RequestStatus.valueOf(status.name())
                : null;
        List<MaintenanceRequest> all = queryService.listByStatus(domainStatus);
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        List<CreateMaintenanceRequest201Response> content = mapper.toCreateResponses(
            all.stream().skip((long) pageNum * pageSize).limit(pageSize).toList());
        PagedRequestDto dto = new PagedRequestDto();
        dto.setContent(content);
        dto.setPage(pageNum);
        dto.setSize(pageSize);
        dto.setTotalElements((long) all.size());
        dto.setTotalPages((int) Math.ceil((double) all.size() / pageSize));
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<RequestDetailDto> getMaintenanceRequest(UUID requestId) {
        MaintenanceRequest request = queryService.getById(requestId);
        List<InspectionReport> inspections = queryService.getInspections(requestId);
        List<Decision> decisions = queryService.getDecisions(requestId);
        Optional<ServiceProvider> provider = queryService.getProvider(request.getAssignedProviderId());
        return ResponseEntity.ok(
            mapper.toRequestDetailDto(request, inspections, decisions, provider.orElse(null)));
    }

    @Override
    public ResponseEntity<CreateMaintenanceRequest201Response> assignServiceProvider(
            UUID requestId, AssignProviderDto body) {
        AssignProviderCommand command = new AssignProviderCommand(
            requestId, body.getProviderId(), correlationId());
        MaintenanceRequest request = assignService.assign(command);
        return ResponseEntity.ok(mapper.toCreateResponse(request));
    }

    @Override
    public ResponseEntity<ListInspections200ResponseInner> submitInspection(
            UUID requestId, com.fleet.maintenance.bff.dto.InspectionReportDto body) {
        List<String> attachments = body.getAttachments() != null
            ? body.getAttachments().stream()
                .filter(uri -> uri != null)
                .map(java.net.URI::toString)
                .toList()
            : List.of();
        Money cost = Money.of(
            BigDecimal.valueOf(body.getEstimatedCost()),
            Money.DEFAULT_CURRENCY);
        SubmitInspectionCommand command = new SubmitInspectionCommand(
            requestId, body.getFindings(), cost,
            body.getEstimatedDurationDays(), attachments, principal(), correlationId());
        InspectionReport report = submitInspectionService.submit(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toInspectionItem(report));
    }

    @Override
    public ResponseEntity<List<ListInspections200ResponseInner>> listInspections(UUID requestId) {
        return ResponseEntity.ok(mapper.toInspectionItems(queryService.getInspections(requestId)));
    }

    @Override
    public ResponseEntity<ListDecisions200ResponseInner> submitDecision(
            UUID requestId, DecisionRequestDto body) {
        DecisionOutcome outcome = DecisionOutcome.valueOf(body.getOutcome().getValue());
        MakeDecisionCommand command = new MakeDecisionCommand(
            requestId, outcome, body.getRemarks(), principal(), correlationId());
        Decision decision = makeDecisionService.decide(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDecisionItem(decision));
    }

    @Override
    public ResponseEntity<List<ListDecisions200ResponseInner>> listDecisions(UUID requestId) {
        return ResponseEntity.ok(mapper.toDecisionItems(queryService.getDecisions(requestId)));
    }

    private UUID correlationId() {
        String header = httpRequest.getHeader(CorrelationIdFilter.HEADER);
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID();
    }

    private String principal() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "system";
    }
}
