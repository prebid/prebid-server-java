package org.prebid.server.health;

import java.util.Map;

public interface HealthChecker {

    String name();

    void initialize();

    Map<String, Object> status();
}
