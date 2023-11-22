package org.prebid.server.spring.dynamic.registry;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.spring.dynamic.PropertyType;
import org.prebid.server.spring.dynamic.properties.DynamicProperty;
import org.prebid.server.spring.dynamic.properties.Property;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicPropertyRegistry implements UpdatablePropertyRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DynamicPropertyRegistry.class);

    private final Map<String, DynamicProperty<?>> properties = new ConcurrentHashMap<>();

    @Override
    public Property<String> getStringProperty(String name, String defaultValue) {
        return getProperty(name, defaultValue);
    }

    @Override
    public Property<Integer> getIntProperty(String name, Integer defaultValue) {
        return getProperty(name, defaultValue);
    }

    @Override
    public Property<Double> getDoubleProperty(String name, Double defaultValue) {
        return getProperty(name, defaultValue);
    }

    @Override
    public Property<Boolean> getBoolProperty(String name, Boolean defaultValue) {
        return getProperty(name, defaultValue);
    }

    @Override
    public Property<ObjectNode> getJsonObjectProperty(String name, ObjectNode defaultValue) {
        return getProperty(name, defaultValue);
    }

    private <T> Property<T> getProperty(String name, T defaultValue) {
        final DynamicProperty<?> property = properties.computeIfAbsent(
                name, ignored -> new DynamicProperty<>(Objects.requireNonNull(defaultValue)));

        validateType(name, property.getType(), PropertyType.from(defaultValue.getClass()));

        return (DynamicProperty<T>) property;
    }

    private static void validateType(String name, PropertyType oldType, PropertyType newType) {
        if (!oldType.equals(newType)) {
            throw new IllegalArgumentException(
                    "Requested property: %s with type: %s, but property already registered with type: %s."
                            .formatted(name, newType.name(), oldType.name()));
        }
    }

    @Override
    public void updateProperty(String name, Object newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException(
                    "Trying to update property: %s with null value, that is not permitted, ignoring.".formatted(name));
        }

        final DynamicProperty<Object> property = (DynamicProperty<Object>) properties.get(name);
        if (property == null) {
            throw new IllegalArgumentException(
                    "Trying to update property: %s, which has not been registered yet, ignoring.".formatted(name));
        }

        if (property.get().equals(newValue)) {
            return;
        }

        final PropertyType newType = PropertyType.from(newValue.getClass());
        if (!newType.matches(newValue)) {
            throw new IllegalArgumentException(
                    "Trying to update property: %s with value: %s of invalid type, required type: %s."
                            .formatted(name, newValue.toString(), newType.name()));

        }

        property.set(newValue);
    }

    @Override
    public void updateProperties(Map<String, Object> properties) {
        properties.forEach((key, value) -> {
            try {
                updateProperty(key, value);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        });
    }
}
