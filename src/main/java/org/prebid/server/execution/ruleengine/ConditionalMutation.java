package org.prebid.server.execution.ruleengine;

import com.iab.openrtb.request.BidRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class ConditionalMutation<T> implements RequestMutation {

    private final Map<T, RequestMutation> subrules;
    private final Function<BidRequest, T> argumentExtractor;
    private final RequestMutation defaultAction;

    public ConditionalMutation(Map<T, RequestMutation> subrules,
                               Function<BidRequest, T> argumentExtractor,
                               RequestMutation defaultAction) {

        this.subrules = new HashMap<>(subrules);
        this.argumentExtractor = Objects.requireNonNull(argumentExtractor);
        this.defaultAction = Objects.requireNonNull(defaultAction);
    }

    @Override
    public BidRequest mutate(BidRequest request) {
        return Optional.ofNullable(argumentExtractor.apply(request))
                .map(subrules::get)
                .orElse(defaultAction)
                .mutate(request);
    }
}
