package com.fleet.maintenance.domain.event;

import java.time.Instant;
import java.util.UUID;

public record EventSummary(
    UUID eventId,
    String eventType,
    Instant timestamp,
    UUID correlationId,
    String payloadJson
) { }
