package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class OperationResult<T> {

    private static final OperationResult<?> EMPTY = OperationResult.builder().build();

    T value;

    List<String> debugMessages;

    @SuppressWarnings("unchecked")
    public static <T> OperationResult<T> empty() {
        return (OperationResult<T>) EMPTY;
    }
}
