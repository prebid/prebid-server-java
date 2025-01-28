package org.prebid.server.execution.ruleengine.extractors;

import lombok.Value;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.function.Function;

@Value(staticConstructor = "of")
public class IntegerExtractor<T> implements ArgumentExtractor<T, Integer> {

    Function<T, Integer> delegate;

    @Override
    public Integer extract(T input) {
        return delegate.apply(input);
    }

    @Override
    public Integer extract(String input) {
        return NumberUtils.toInt(input);
    }

}
