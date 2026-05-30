package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.model.ServiceProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceProviderRepository {

    Optional<ServiceProvider> findById(UUID providerId);

    List<ServiceProvider> findAllActive();
}
