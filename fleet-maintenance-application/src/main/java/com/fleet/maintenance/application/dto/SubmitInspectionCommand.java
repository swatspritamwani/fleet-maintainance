package com.fleet.maintenance.application.dto;

import com.fleet.maintenance.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SubmitInspectionCommand(
    UUID requestId,
    String findings,
    Money estimatedCost,
    int estimatedDurationDays,
    List<String> attachments,
    String submittedBy,
    UUID correlationId
) {

    public SubmitInspectionCommand {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(findings, "findings must not be null");
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        Objects.requireNonNull(submittedBy, "submittedBy must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        attachments = (attachments != null) ? List.copyOf(attachments) : List.of();
    }
}
