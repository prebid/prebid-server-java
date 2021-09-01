package org.prebid.server.hooks.modules.ortb2.blocking.core.util;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.Result;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface MergeUtils {

    static <T> List<String> mergeMessages(List<? extends Result<?>> results) {
        return mergeMessages(results.stream());
    }

    static <T> List<String> mergeMessages(Result<?>... results) {
        return mergeMessages(Arrays.stream(results));
    }

    static <T> List<String> mergeMessages(Stream<? extends Result<?>> results) {
        final List<String> warnings = results
            .map(Result::getMessages)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toList());

        return !warnings.isEmpty() ? warnings : null;
    }

    static <T> List<T> merge(List<T> defaultValues, List<T> overriddenValues) {
        if (overriddenValues == null) {
            return defaultValues;
        }
        if (defaultValues == null) {
            return overriddenValues;
        }

        return ListUtils.union(defaultValues, overriddenValues);
    }
}
