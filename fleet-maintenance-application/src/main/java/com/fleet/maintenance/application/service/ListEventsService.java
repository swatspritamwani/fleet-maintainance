package com.fleet.maintenance.application.service;

import com.fleet.maintenance.domain.event.EventSummary;
import com.fleet.maintenance.domain.port.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ListEventsService {

    private final EventRepository eventRepository;

    public ListEventsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventSummary> list(String eventType, Instant since, int page, int size) {
        return eventRepository.findPublished(eventType, since, page * size, size);
    }

    public long count(String eventType, Instant since) {
        return eventRepository.countPublished(eventType, since);
    }
}
