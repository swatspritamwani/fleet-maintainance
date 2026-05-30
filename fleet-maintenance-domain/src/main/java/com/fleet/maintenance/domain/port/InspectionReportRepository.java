package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.model.InspectionReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionReportRepository {

    List<InspectionReport> findByRequestId(UUID requestId);

    Optional<InspectionReport> findLatestByRequestId(UUID requestId);
}
