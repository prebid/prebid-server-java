package org.prebid.server.health;

import java.util.Collections;
import java.util.Map;

public class ApplicationChecker implements HealthChecker {

    private static final String NAME = "application";
    private static final String STATUS_KEY = "status";

    private final String status;

    public ApplicationChecker(String status) {
        this.status = status;
    }

    @Override
    public Map<String, Object> status() {
        return Collections.singletonMap(STATUS_KEY, status);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize() {
        // do nothing as the value is being read from config file and status gets set in a constructor
    }
}
