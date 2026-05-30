package com.fleet.maintenance.bff.controller;

import com.fleet.maintenance.application.service.ListEventsService;
import com.fleet.maintenance.bff.config.SecurityConfig;
import com.fleet.maintenance.bff.dto.PagedEventDtoContentInner;
import com.fleet.maintenance.bff.mapper.BffMapper;
import com.fleet.maintenance.domain.event.EventSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    private static final int SMALL_PAGE_SIZE = 5;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListEventsService listEventsService;

    @MockBean
    private BffMapper mapper;

    @Test
    void listEvents_returns200WithEmptyContent() throws Exception {
        when(listEventsService.list(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(listEventsService.count(isNull(), isNull())).thenReturn(0L);

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listEvents_returnsPagedResults() throws Exception {
        EventSummary summary = new EventSummary(
            UUID.randomUUID(), "maintenance.request.created",
            Instant.now(), UUID.randomUUID(), "{}");
        when(listEventsService.list(isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of(summary));
        when(listEventsService.count(isNull(), isNull())).thenReturn(1L);
        PagedEventDtoContentInner item = new PagedEventDtoContentInner();
        item.setEventType(PagedEventDtoContentInner.EventTypeEnum.fromValue("maintenance.request.created"));
        when(mapper.toEventItem(any())).thenReturn(item);

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].eventType").value("maintenance.request.created"));
    }

    @Test
    void listEvents_withEventTypeFilter() throws Exception {
        when(listEventsService.list(any(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(listEventsService.count(any(), isNull())).thenReturn(0L);

        mockMvc.perform(get("/api/v1/events")
                .param("eventType", "maintenance.request.created"))
            .andExpect(status().isOk());
    }

    @Test
    void listEvents_withPageAndSize() throws Exception {
        when(listEventsService.list(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(listEventsService.count(isNull(), isNull())).thenReturn(0L);

        mockMvc.perform(get("/api/v1/events").param("page", "1").param("size",
                String.valueOf(SMALL_PAGE_SIZE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(SMALL_PAGE_SIZE));
    }
}
