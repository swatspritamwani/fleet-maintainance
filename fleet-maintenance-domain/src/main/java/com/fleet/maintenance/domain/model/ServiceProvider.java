package com.fleet.maintenance.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ServiceProvider(
    UUID providerId,
    String name,
    String contactEmail,
    String phone,
    boolean active
) {

    public ServiceProvider {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
