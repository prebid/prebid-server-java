package org.prebid.server.execution.ruleengine;

import org.prebid.server.execution.ruleengine.extractors.ArgumentExtractor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ConditionalMutation<T, R> implements Mutation<T> {

    private final Map<R, Mutation<T>> subrules;
    private final ArgumentExtractor<T, R> argumentExtractor;
    private final Mutation<T> defaultAction;

    public ConditionalMutation(Map<R, Mutation<T>> subrules,
                               ArgumentExtractor<T, R> argumentExtractor,
                               Mutation<T> defaultAction) {

        this.subrules = new HashMap<>(subrules);
        this.argumentExtractor = Objects.requireNonNull(argumentExtractor);
        this.defaultAction = Objects.requireNonNull(defaultAction);
    }

    @Override
    public T mutate(T input) {
        return Optional.ofNullable(argumentExtractor.extract(input))
                .map(subrules::get)
                .orElse(defaultAction)
                .mutate(input);
    }
}
