package org.prebid.server.adapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all {@link Adapter}s registered so far.
 */
public class AdapterCatalog {

    private final Map<String, Adapter> adapters;

    public AdapterCatalog(List<Adapter> adapters) {
        this.adapters = Objects.requireNonNull(adapters).stream()
                .collect(Collectors.toMap(Adapter::name, Function.identity()));
    }

    /**
     * Returns {@link Adapter} by name. If adapter with given code is not exist then null will be returned.
     * For null-safety {@link #isValidName(String)} can be used.
     */
    public Adapter byName(String name) {
        return adapters.get(name);
    }

    /**
     * Returns true if adapter with given code exists otherwise false.
     */
    public boolean isValidName(String name) {
        return adapters.containsKey(name);
    }
}
