package com.fleet.maintenance.bff.mapper;

import com.fleet.maintenance.bff.dto.CreateMaintenanceRequest201Response;
import com.fleet.maintenance.bff.dto.DecisionDto;
import com.fleet.maintenance.bff.dto.InspectionDto;
import com.fleet.maintenance.bff.dto.ListDecisions200ResponseInner;
import com.fleet.maintenance.bff.dto.ListInspections200ResponseInner;
import com.fleet.maintenance.bff.dto.ListServiceProviders200ResponseInner;
import com.fleet.maintenance.bff.dto.PagedEventDtoContentInner;
import com.fleet.maintenance.bff.dto.RequestDetailDto;
import com.fleet.maintenance.bff.dto.RequestDto;
import com.fleet.maintenance.domain.event.EventSummary;
import com.fleet.maintenance.domain.model.Decision;
import com.fleet.maintenance.domain.model.InspectionReport;
import com.fleet.maintenance.domain.model.MaintenanceRequest;
import com.fleet.maintenance.domain.model.ServiceProvider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface BffMapper {

    @Mapping(source = "requestId", target = "requestId")
    @Mapping(source = "vehicleId", target = "vehicleId")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "priority", target = "priority")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "assignedProviderId", target = "assignedProviderId")
    @Mapping(source = "createdBy", target = "createdBy")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    CreateMaintenanceRequest201Response toCreateResponse(MaintenanceRequest request);

    @Mapping(source = "requestId", target = "requestId")
    @Mapping(source = "vehicleId", target = "vehicleId")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "priority", target = "priority")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "assignedProviderId", target = "assignedProviderId")
    @Mapping(source = "createdBy", target = "createdBy")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    RequestDto toRequestDto(MaintenanceRequest request);

    List<RequestDto> toRequestDtos(List<MaintenanceRequest> requests);

    List<CreateMaintenanceRequest201Response> toCreateResponses(List<MaintenanceRequest> requests);

    @Mapping(source = "estimatedCost.amount", target = "estimatedCost.amount")
    @Mapping(source = "estimatedCost.currency", target = "estimatedCost.currency")
    InspectionDto toInspectionDto(InspectionReport report);

    List<InspectionDto> toInspectionDtos(List<InspectionReport> reports);

    @Mapping(source = "estimatedCost.amount", target = "estimatedCost.amount")
    @Mapping(source = "estimatedCost.currency", target = "estimatedCost.currency")
    ListInspections200ResponseInner toInspectionItem(InspectionReport report);

    List<ListInspections200ResponseInner> toInspectionItems(List<InspectionReport> reports);

    DecisionDto toDecisionDto(Decision decision);

    List<DecisionDto> toDecisionDtos(List<Decision> decisions);

    ListDecisions200ResponseInner toDecisionItem(Decision decision);

    List<ListDecisions200ResponseInner> toDecisionItems(List<Decision> decisions);

    ListServiceProviders200ResponseInner toProviderItem(ServiceProvider provider);

    List<ListServiceProviders200ResponseInner> toProviderItems(List<ServiceProvider> providers);

    @Mapping(source = "eventId", target = "eventId")
    @Mapping(source = "eventType", target = "eventType")
    @Mapping(source = "timestamp", target = "timestamp")
    @Mapping(source = "correlationId", target = "correlationId")
    @Mapping(target = "payload", ignore = true)
    PagedEventDtoContentInner toEventItem(EventSummary summary);

    default com.fleet.maintenance.bff.dto.PagedEventDtoContentInner.EventTypeEnum toEventTypeEnum(
            String eventType) {
        return eventType == null ? null
            : com.fleet.maintenance.bff.dto.PagedEventDtoContentInner.EventTypeEnum
                .fromValue(eventType);
    }

    default com.fleet.maintenance.bff.dto.Priority toDto(
            com.fleet.maintenance.domain.model.Priority p) {
        return p == null ? null : com.fleet.maintenance.bff.dto.Priority.valueOf(p.name());
    }

    default com.fleet.maintenance.bff.dto.RequestStatus toDto(
            com.fleet.maintenance.domain.model.RequestStatus s) {
        return s == null ? null : com.fleet.maintenance.bff.dto.RequestStatus.valueOf(s.name());
    }

    default com.fleet.maintenance.bff.dto.DecisionOutcome toDto(
            com.fleet.maintenance.domain.model.DecisionOutcome o) {
        return o == null ? null : com.fleet.maintenance.bff.dto.DecisionOutcome.valueOf(o.name());
    }

    default com.fleet.maintenance.domain.model.DecisionOutcome toDomain(
            com.fleet.maintenance.bff.dto.DecisionOutcome o) {
        return o == null ? null : com.fleet.maintenance.domain.model.DecisionOutcome.valueOf(o.name());
    }

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    default OffsetDateTime toOffsetDateTime(UUID uuid) {
        return null;
    }

    default java.net.URI toUri(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    default String toString(java.net.URI uri) {
        return uri == null ? null : uri.toString();
    }

    default RequestDetailDto toRequestDetailDto(
            MaintenanceRequest request,
            List<InspectionReport> inspections,
            List<Decision> decisions,
            ServiceProvider provider) {
        RequestDetailDto dto = new RequestDetailDto();
        dto.setRequestId(request.getRequestId());
        dto.setVehicleId(request.getVehicleId());
        dto.setDescription(request.getDescription());
        dto.setPriority(toDto(request.getPriority()));
        dto.setStatus(toDto(request.getStatus()));
        dto.setAssignedProviderId(request.getAssignedProviderId());
        dto.setCreatedBy(request.getCreatedBy());
        dto.setCreatedAt(toOffsetDateTime(request.getCreatedAt()));
        dto.setUpdatedAt(toOffsetDateTime(request.getUpdatedAt()));
        dto.setInspections(toInspectionItems(inspections));
        dto.setDecisions(toDecisionItems(decisions));
        if (provider != null) {
            dto.setAssignedProvider(toProviderItem(provider));
        }
        return dto;
    }
}
