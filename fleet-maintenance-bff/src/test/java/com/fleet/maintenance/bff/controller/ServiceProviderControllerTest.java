package com.fleet.maintenance.bff.controller;

import com.fleet.maintenance.application.service.ListServiceProvidersService;
import com.fleet.maintenance.bff.config.SecurityConfig;
import com.fleet.maintenance.bff.dto.ListServiceProviders200ResponseInner;
import com.fleet.maintenance.bff.mapper.BffMapper;
import com.fleet.maintenance.domain.model.ServiceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceProviderController.class)
@Import(SecurityConfig.class)
class ServiceProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListServiceProvidersService listServiceProvidersService;

    @MockBean
    private BffMapper mapper;

    @Test
    void listServiceProviders_returnsEmptyList() throws Exception {
        when(listServiceProvidersService.listActive()).thenReturn(List.of());
        when(mapper.toProviderItems(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/service-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listServiceProviders_returnsActiveProviders() throws Exception {
        ServiceProvider provider = new ServiceProvider(
            UUID.randomUUID(), "AutoFix", "auto@fix.com", "555-1000", true);
        when(listServiceProvidersService.listActive()).thenReturn(List.of(provider));
        ListServiceProviders200ResponseInner item = new ListServiceProviders200ResponseInner();
        item.setName("AutoFix");
        item.setActive(true);
        when(mapper.toProviderItems(any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/service-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("AutoFix"))
            .andExpect(jsonPath("$[0].active").value(true));
    }
}
