package com.fleet.maintenance.domain.valueobject;

import java.util.Objects;

public record VehicleRef(String vehicleId, String licensePlate, String make, String model) {

    public VehicleRef {
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    }
}
