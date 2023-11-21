package org.prebid.server.spring.dynamic;

import java.util.Arrays;

public enum DynamicPropertyType {

    STRING(String.class), DOUBLE(Double.class), INT(Integer.class), BOOL(Boolean.class);

    private final Class<?> propertyClass;

    DynamicPropertyType(Class<?> clazz) {
        this.propertyClass = clazz;
    }

    public boolean matches(Object value) {
        return propertyClass.isInstance(value);
    }

    public static DynamicPropertyType from(Class<?> clazz) {
        return Arrays.stream(values())
                .filter(value -> value.propertyClass == clazz)
                .findAny()
                .orElseThrow();
    }
}
