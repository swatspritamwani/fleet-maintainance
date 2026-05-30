package com.fleet.maintenance.application.dto;

import java.util.Objects;
import java.util.UUID;

public record AssignProviderCommand(
    UUID requestId,
    UUID providerId,
    UUID correlationId
) {

    public AssignProviderCommand {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
