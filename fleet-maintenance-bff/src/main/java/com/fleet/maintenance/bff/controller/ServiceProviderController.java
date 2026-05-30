package com.fleet.maintenance.bff.controller;

import com.fleet.maintenance.application.service.ListServiceProvidersService;
import com.fleet.maintenance.bff.api.ServiceProvidersApi;
import com.fleet.maintenance.bff.dto.ListServiceProviders200ResponseInner;
import com.fleet.maintenance.bff.mapper.BffMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ServiceProviderController implements ServiceProvidersApi {

    private final ListServiceProvidersService listServiceProvidersService;
    private final BffMapper mapper;

    public ServiceProviderController(
            ListServiceProvidersService listServiceProvidersService, BffMapper mapper) {
        this.listServiceProvidersService = listServiceProvidersService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ListServiceProviders200ResponseInner>> listServiceProviders() {
        return ResponseEntity.ok(
            mapper.toProviderItems(listServiceProvidersService.listActive()));
    }
}
