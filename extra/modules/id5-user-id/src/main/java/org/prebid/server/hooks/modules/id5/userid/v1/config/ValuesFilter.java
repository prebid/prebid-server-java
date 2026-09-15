package org.prebid.server.hooks.modules.id5.userid.v1.config;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

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
        return CollectionUtils.isEmpty(values)
                || (value != null && exclude != values.contains(value));
    }
}
