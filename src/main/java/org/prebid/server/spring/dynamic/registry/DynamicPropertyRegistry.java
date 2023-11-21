package org.prebid.server.spring.dynamic.registry;

import lombok.extern.slf4j.Slf4j;
import org.prebid.server.spring.dynamic.CompositePropertyUpdateListener;
import org.prebid.server.spring.dynamic.PropertyType;
import org.prebid.server.spring.dynamic.properties.DynamicProperty;
import org.prebid.server.spring.dynamic.properties.Property;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DynamicPropertyRegistry implements UpdatablePropertyRegistry {

    private static final CompositePropertyUpdateListener<Object> EMPTY_LISTENER =
            new CompositePropertyUpdateListener<>();

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Map<String, CompositePropertyUpdateListener<Object>> propertyUpdateListeners =
            new ConcurrentHashMap<>();
    private final Map<String, PropertyType> propertyTypes = new ConcurrentHashMap<>();

    @Override
    public Property<String> getStringProperty(String name, String defaultValue) {
        return getProperty(name, defaultValue, String.class);
    }

    @Override
    public Property<Integer> getIntProperty(String name, Integer defaultValue) {
        return getProperty(name, defaultValue, Integer.class);
    }

    @Override
    public Property<Double> getDoubleProperty(String name, Double defaultValue) {
        return getProperty(name, defaultValue, Double.class);
    }

    @Override
    public Property<Boolean> getBoolProperty(String name, Boolean defaultValue) {
        return getProperty(name, defaultValue, Boolean.class);
    }

    private <T> Property<T> getProperty(String name, T defaultValue, Class<T> clazz) {
        propertyTypes.compute(name, (key, existingType) -> resolveType(key, existingType, PropertyType.from(clazz)));

        final T initial = clazz.cast(properties.computeIfAbsent(name, ignored -> defaultValue));
        final DynamicProperty<T> property = new DynamicProperty<>(initial);

        // We may miss one property update if update comes before we added listener for it
        propertyUpdateListeners.computeIfAbsent(name, ignored -> new CompositePropertyUpdateListener<>())
                .addListener(newValue -> property.set(clazz.cast(newValue)));

        return property;
    }

    private static PropertyType resolveType(String name, PropertyType oldType, PropertyType newType) {
        if (oldType == null || oldType.equals(newType)) {
            return newType;
        } else {
            throw new IllegalArgumentException(
                    "Requested property: %s with type: %s, but property already registered with type: %s."
                            .formatted(name, newType.name(), oldType.name()));
        }
    }

    @Override
    public void updateProperty(String name, Object newValue) {
        final Object oldValue = properties.get(name);
        if (oldValue == newValue) {
            return;
        }

        final PropertyType type = propertyTypes.get(name);
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

    @Override
    public void updateProperties(Map<String, Object> properties) {
        properties.forEach(this::updateProperty);
    }
}
