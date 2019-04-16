package org.prebid.server.health;

import java.util.Map;

public interface HealthChecker {

    Map<String, Object> status();

    String name();

    void initialize();
}
