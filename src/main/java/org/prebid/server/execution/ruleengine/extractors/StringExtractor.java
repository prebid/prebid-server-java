package org.prebid.server.execution.ruleengine.extractors;

import lombok.Value;

import java.util.function.Function;

@Value(staticConstructor = "of")
public class StringExtractor<T> implements ArgumentExtractor<T, String> {

    Function<T, String> delegate;

    @Override
    public String extract(T input) {
        return delegate.apply(input);
    }

    @Override
    public String extract(String input) {
        return input;
    }
}
