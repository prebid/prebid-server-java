package org.prebid.server.execution.ruleengine.extractors;

import lombok.Value;

import java.util.function.Function;

@Value(staticConstructor = "of")
public class BooleanExtractor<T> implements ArgumentExtractor<T, Boolean> {

    Function<T, Boolean> delegate;

    @Override
    public Boolean extract(T input) {
        return delegate.apply(input);
    }

    @Override
    public Boolean extract(String input) {
        return Boolean.parseBoolean(input);
    }
}
