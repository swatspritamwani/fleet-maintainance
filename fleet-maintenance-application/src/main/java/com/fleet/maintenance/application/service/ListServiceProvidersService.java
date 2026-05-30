package com.fleet.maintenance.application.service;

import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListServiceProvidersService {

    private final ServiceProviderRepository serviceProviderRepository;

    public ListServiceProvidersService(ServiceProviderRepository serviceProviderRepository) {
        this.serviceProviderRepository = serviceProviderRepository;
    }

    public List<ServiceProvider> listActive() {
        return serviceProviderRepository.findAllActive();
    }
}
