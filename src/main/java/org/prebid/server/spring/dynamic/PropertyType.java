package org.prebid.server.spring.dynamic;

import java.util.Arrays;

public enum PropertyType {

    STRING(String.class), DOUBLE(Double.class), INT(Integer.class), BOOL(Boolean.class);

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
                .orElseThrow();
    }
}
