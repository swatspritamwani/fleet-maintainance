package com.fleet.maintenance.domain.model;

import com.fleet.maintenance.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Decision(
    UUID decisionId,
    UUID requestId,
    DecisionOutcome outcome,
    String remarks,
    String decidedBy,
    Instant decidedAt
) {

    public Decision {
        Objects.requireNonNull(decisionId, "decisionId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (requiresRemarks(outcome) && (remarks == null || remarks.isBlank())) {
            throw new DomainValidationException("remarks required for outcome: " + outcome);
        }
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    private static boolean requiresRemarks(DecisionOutcome outcome) {
        return outcome == DecisionOutcome.REJECTED || outcome == DecisionOutcome.INFO_REQUESTED;
    }
}
