package org.prebid.server.spring.dynamic;

import org.prebid.server.spring.dynamic.properties.DynamicProperty;
import org.prebid.server.spring.dynamic.properties.Property;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicPropertyFactory {

    private static final CompositeDynamicPropertyUpdateListener<Object> EMPTY_LISTENER =
            new CompositeDynamicPropertyUpdateListener<>();

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Map<String, CompositeDynamicPropertyUpdateListener<Object>> propertyUpdateListeners =
            new ConcurrentHashMap<>();
    private final Map<String, DynamicPropertyType> propertyTypes = new ConcurrentHashMap<>();

    public Property<String> getStringProperty(String name, String defaultValue) {
        return getProperty(name, defaultValue, String.class);
    }

    public Property<Integer> getIntProperty(String name, Integer defaultValue) {
        return getProperty(name, defaultValue, Integer.class);
    }

    public Property<Double> getDoubleProperty(String name, Double defaultValue) {
        return getProperty(name, defaultValue, Double.class);
    }

    public Property<Boolean> getBoolProperty(String name, Boolean defaultValue) {
        return getProperty(name, defaultValue, Boolean.class);
    }

    private <T> Property<T> getProperty(String name, T defaultValue, Class<T> clazz) {
        final DynamicPropertyType propertyType = DynamicPropertyType.from(clazz);
        propertyTypes.compute(name, (ignored, oldValue) -> {
            if (oldValue == null || oldValue.equals(propertyType)) {
                return propertyType;
            } else {
                throw new IllegalArgumentException(
                        "Requested property: %s with type: %s, but property already registered with type: %s."
                                .formatted(name, propertyType.name(), oldValue.name()));
            }
        });

        final T propertyValue = clazz.cast(properties.computeIfAbsent(name, ignored -> defaultValue));
        final DynamicProperty<T> property = new DynamicProperty<>(propertyValue);

        // We may miss one property update if update comes before we added listener for it
        propertyUpdateListeners.computeIfAbsent(name, ignored -> new CompositeDynamicPropertyUpdateListener<>())
                .addListener(newValue -> property.set(clazz.cast(newValue)));

        return property;
    }

    public void updateProperty(String name, Object newValue) {
        final DynamicPropertyType type = propertyTypes.get(name);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Trying to update property: %s, which has not been registered yet, ignoring.");
        }

        if (!type.matches(newValue)) {
            throw new IllegalArgumentException(
                    "Trying to update property: %s with value: %s of invalid type, required type: %s."
                            .formatted(name, newValue.toString(), type.name()));
        }

        properties.put(name, newValue);
        propertyUpdateListeners.getOrDefault(name, EMPTY_LISTENER).onUpdate(newValue);
    }
}
