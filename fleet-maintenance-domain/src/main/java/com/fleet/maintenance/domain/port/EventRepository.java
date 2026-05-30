package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.event.EventSummary;

import java.time.Instant;
import java.util.List;

public interface EventRepository {
    List<EventSummary> findPublished(String eventType, Instant since, int offset, int limit);
    long countPublished(String eventType, Instant since);
}
