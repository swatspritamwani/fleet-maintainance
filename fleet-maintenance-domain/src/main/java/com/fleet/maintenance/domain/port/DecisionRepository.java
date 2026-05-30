package com.fleet.maintenance.domain.port;

import com.fleet.maintenance.domain.model.Decision;

import java.util.List;
import java.util.UUID;

public interface DecisionRepository {

    List<Decision> findByRequestId(UUID requestId);
}
