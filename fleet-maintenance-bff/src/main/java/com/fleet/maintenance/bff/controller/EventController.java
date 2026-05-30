package com.fleet.maintenance.bff.controller;

import com.fleet.maintenance.application.service.ListEventsService;
import com.fleet.maintenance.bff.api.EventsApi;
import com.fleet.maintenance.bff.dto.PagedEventDto;
import com.fleet.maintenance.bff.dto.PagedEventDtoContentInner;
import com.fleet.maintenance.bff.mapper.BffMapper;
import com.fleet.maintenance.domain.event.EventSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
public class EventController implements EventsApi {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ListEventsService listEventsService;
    private final BffMapper mapper;

    public EventController(ListEventsService listEventsService, BffMapper mapper) {
        this.listEventsService = listEventsService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PagedEventDto> listEvents(
            Integer page, Integer size, String eventType, OffsetDateTime since) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        Instant sinceInstant = since != null ? since.toInstant() : null;
        List<EventSummary> events = listEventsService.list(eventType, sinceInstant, pageNum, pageSize);
        long total = listEventsService.count(eventType, sinceInstant);
        List<PagedEventDtoContentInner> content = events.stream()
            .map(mapper::toEventItem)
            .toList();
        PagedEventDto dto = new PagedEventDto();
        dto.setContent(content);
        dto.setPage(pageNum);
        dto.setSize(pageSize);
        dto.setTotalElements(total);
        dto.setTotalPages((int) Math.ceil((double) total / pageSize));
        return ResponseEntity.ok(dto);
    }
}
