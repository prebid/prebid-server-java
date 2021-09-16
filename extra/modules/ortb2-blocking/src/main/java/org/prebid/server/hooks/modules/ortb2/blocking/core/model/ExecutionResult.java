package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Builder
@Value
public class ExecutionResult<T> {

    private static final ExecutionResult<?> EMPTY = ExecutionResult.builder().build();

    T value;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;

    List<AnalyticsResult> analyticsResults;

    public boolean hasValue() {
        return value != null;
    }

    @SuppressWarnings("unchecked")
    public static <T> ExecutionResult<T> empty() {
        return (ExecutionResult<T>) EMPTY;
    }

    public static <T> ExecutionResult<T> withError(String error) {
        return ExecutionResult.<T>builder()
            .errors(Collections.singletonList(error))
            .build();
    }
}
