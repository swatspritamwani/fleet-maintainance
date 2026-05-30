package com.fleet.maintenance.application.dto;

import com.fleet.maintenance.domain.model.DecisionOutcome;

import java.util.Objects;
import java.util.UUID;

public record MakeDecisionCommand(
    UUID requestId,
    DecisionOutcome outcome,
    String remarks,
    String decidedBy,
    UUID correlationId
) {

    public MakeDecisionCommand {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
