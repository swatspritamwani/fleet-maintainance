package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.event.DomainEvent;

import java.util.UUID;

public interface DomainEventPublisher {

    void publish(DomainEvent event, UUID correlationId);
}
