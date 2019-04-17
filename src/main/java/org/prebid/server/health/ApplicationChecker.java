package org.prebid.server.health;

import org.prebid.server.health.model.StatusResponse;

public class ApplicationChecker implements HealthChecker {

    private static final String NAME = "application";

    private final String status;

    public ApplicationChecker(String status) {
        this.status = status;
    }

    @Override
    public StatusResponse status() {
        return StatusResponse.of(status, null);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize() {
        // do nothing as the status value is being read from config file and gets set in the constructor
    }
}
