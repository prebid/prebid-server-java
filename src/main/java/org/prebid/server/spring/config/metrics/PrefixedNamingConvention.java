package org.prebid.server.spring.config.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class PrefixedNamingConvention implements NamingConvention {

    private final String prefix;
    private final NamingConvention delegate;

    public PrefixedNamingConvention(String prefix, NamingConvention delegate) {
        this.prefix = StringUtils.isNotBlank(prefix) ? prefix + "." : "";
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return prefix + delegate.name(name, type, baseUnit);
    }
}
