package org.prebid.server.health;

import org.prebid.server.health.model.StatusResponse;

public interface HealthChecker {

    StatusResponse getLastStatus();

    String getCheckName();

    void initialize();
}
