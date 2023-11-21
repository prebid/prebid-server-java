package org.prebid.server.spring.dynamic.registry;

import org.prebid.server.spring.dynamic.properties.Property;

public interface PropertyRegistry {

    Property<String> getStringProperty(String name, String defaultValue);

    Property<Integer> getIntProperty(String name, Integer defaultValue);

    Property<Double> getDoubleProperty(String name, Double defaultValue);

    Property<Boolean> getBoolProperty(String name, Boolean defaultValue);
}
