package org.prebid.server.hooks.modules.id5.userid.v1.config;

import lombok.Data;

import java.util.Set;

@Data
public class ValuesFilter<T> {

    private boolean exclude = false;
    private Set<T> values;

    /**
     * Determines whether a value is allowed based on include/exclude semantics.
     * If the set of values is null or empty, no filtering is applied (always allowed).
     * Null value is not allowed
     */
    public boolean isValueAllowed(T value) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return exclude != values.contains(value);
    }
}
