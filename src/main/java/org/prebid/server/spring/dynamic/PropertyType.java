package org.prebid.server.spring.dynamic;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;

public enum PropertyType {

    STRING(String.class),
    DOUBLE(Double.class),
    INT(Integer.class),
    BOOL(Boolean.class),
    JSON_OBJECT(ObjectNode.class);

    private final Class<?> propertyClass;

    PropertyType(Class<?> clazz) {
        this.propertyClass = clazz;
    }

    public boolean matches(Object value) {
        return propertyClass.isInstance(value);
    }

    public static PropertyType from(Class<?> clazz) {
        return Arrays.stream(values())
                .filter(value -> value.propertyClass == clazz)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported property type"));
    }
}
