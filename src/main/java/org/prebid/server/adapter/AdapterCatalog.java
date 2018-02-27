package org.prebid.server.adapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all adapters registered so far.
 */
public class AdapterCatalog {

    private final Map<String, Adapter> adapters;

    public AdapterCatalog(List<Adapter> adapterList) {
        this.adapters = Objects.requireNonNull(adapterList).stream()
                .collect(Collectors.toMap(Adapter::code, Function.identity()));
    }

    /**
     * Returns {@link Adapter} by code. If adapter with given code is not exist then null will be returned.
     * For null-safety {@link #isValidCode(String)} can be used.
     */
    public Adapter getByCode(String code) {
        return adapters.get(code);
    }

    /**
     * Returns true if adapter with given code exists otherwise false.
     */
    public boolean isValidCode(String code) {
        return adapters.containsKey(code);
    }
}
