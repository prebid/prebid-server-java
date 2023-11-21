package org.prebid.server.spring.dynamic.registry;

import java.util.Map;

public interface UpdatablePropertyRegistry extends PropertyRegistry {

    void updateProperty(String name, Object newValue);

    void updateProperties(Map<String, Object> properties);
}
