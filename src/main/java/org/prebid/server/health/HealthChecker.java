package org.prebid.server.health;

import org.prebid.server.health.model.StatusResponse;

public interface HealthChecker {

    String name();

    void initialize();

    StatusResponse status();
}
