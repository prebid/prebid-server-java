package org.prebid.server.health;

import org.prebid.server.health.model.StatusResponse;

public class ApplicationChecker implements HealthChecker {

    private static final String NAME = "application";

    private final StatusResponse status;

    public ApplicationChecker(String status) {
        this.status = StatusResponse.of(status, null);
    }

    @Override
    public StatusResponse status() {
        return status;
    }

    @Override
    public String name() {
        return NAME;
    }
}
