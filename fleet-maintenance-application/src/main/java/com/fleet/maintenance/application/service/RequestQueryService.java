package com.fleet.maintenance.application.service;

import com.fleet.maintenance.domain.exception.NotFoundException;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.RequestStatus;
import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.DecisionRepository;
import com.fleet.maintenance.domain.port.InspectionReportRepository;
import com.fleet.maintenance.domain.port.MaintenanceRequestRepository;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RequestQueryService {

    private final MaintenanceRequestRepository requestRepository;
    private final InspectionReportRepository inspectionReportRepository;
    private final DecisionRepository decisionRepository;
    private final ServiceProviderRepository serviceProviderRepository;

    public RequestQueryService(
            MaintenanceRequestRepository requestRepository,
            InspectionReportRepository inspectionReportRepository,
            DecisionRepository decisionRepository,
            ServiceProviderRepository serviceProviderRepository) {
        this.requestRepository = requestRepository;
        this.inspectionReportRepository = inspectionReportRepository;
        this.decisionRepository = decisionRepository;
        this.serviceProviderRepository = serviceProviderRepository;
    }

    public MaintenanceRequest getById(UUID requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));
    }

    public List<InspectionReport> getInspections(UUID requestId) {
        getById(requestId);
        return inspectionReportRepository.findByRequestId(requestId);
    }

    public List<Decision> getDecisions(UUID requestId) {
        getById(requestId);
        return decisionRepository.findByRequestId(requestId);
    }

    public Optional<ServiceProvider> getProvider(UUID providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return serviceProviderRepository.findById(providerId);
    }

    public List<MaintenanceRequest> listByStatus(RequestStatus status) {
        if (status != null) {
            return requestRepository.findByStatus(status);
        }
        return requestRepository.findAll();
    }
}
