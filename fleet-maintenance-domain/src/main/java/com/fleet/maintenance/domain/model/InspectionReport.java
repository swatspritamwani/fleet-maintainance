package com.fleet.maintenance.domain.model;

import com.fleet.maintenance.domain.exception.DomainValidationException;
import com.fleet.maintenance.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InspectionReport(
    UUID reportId,
    UUID requestId,
    String findings,
    Money estimatedCost,
    int estimatedDurationDays,
    List<String> attachments,
    Instant submittedAt,
    String submittedBy
) {

    static final int MAX_FINDINGS_LENGTH = 5000;

    public InspectionReport {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (findings == null || findings.isBlank()) {
            throw new DomainValidationException("findings must not be blank");
        }
        if (findings.length() > MAX_FINDINGS_LENGTH) {
            throw new DomainValidationException("findings exceeds " + MAX_FINDINGS_LENGTH + " characters");
        }
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        if (estimatedDurationDays < 1) {
            throw new DomainValidationException("estimatedDurationDays must be >= 1");
        }
        attachments = (attachments != null) ? List.copyOf(attachments) : List.of();
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(submittedBy, "submittedBy must not be null");
    }
}
