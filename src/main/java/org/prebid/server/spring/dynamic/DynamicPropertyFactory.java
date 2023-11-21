package org.prebid.server.spring.dynamic;

import org.prebid.server.spring.dynamic.properties.DynamicStringProperty;
import org.prebid.server.spring.dynamic.properties.Property;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicPropertyFactory {

    private static final CompositeDynamicPropertyUpdateListener<Object> EMPTY_LISTENER =
            new CompositeDynamicPropertyUpdateListener<>();

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Map<String, CompositeDynamicPropertyUpdateListener<Object>> propertyUpdateListeners =
            new ConcurrentHashMap<>();

    public Property<String> getStringProperty(String name, String defaultValue) {
        final String propertyValue = (String) properties.computeIfAbsent(name, ignored -> defaultValue);
        final DynamicStringProperty property = new DynamicStringProperty(propertyValue);

        // We may miss one property update if update comes before we added listener for it
        propertyUpdateListeners.computeIfAbsent(name, ignored -> new CompositeDynamicPropertyUpdateListener<>())
                .addListener(newValue -> property.set((String) newValue));

        return property;
    }

    public void updateProperty(String name, Object newValue) {
        // TODO: add type checks
        properties.put(name, newValue);
        propertyUpdateListeners.getOrDefault(name, EMPTY_LISTENER).onUpdate(newValue);
    }
}
