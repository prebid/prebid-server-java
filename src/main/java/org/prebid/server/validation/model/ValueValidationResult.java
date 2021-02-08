package org.prebid.server.validation.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value(staticConstructor = "of")
public class ValueValidationResult<T> {

    T value;

    List<String> warnings;

    List<String> errors;

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> warningsAndErrors() {
        return Stream.concat(warnings.stream(), errors.stream())
                .collect(Collectors.toList());
    }

    public static <T> ValueValidationResult<T> error(String errorMessageFormat, Object... args) {
        final String message = String.format(errorMessageFormat, args);
        return new ValueValidationResult<>(null, Collections.emptyList(), Collections.singletonList(message));
    }

    public static <T> ValueValidationResult<T> warning(T value, List<String> warnings) {
        return new ValueValidationResult<>(value, warnings, Collections.emptyList());
    }

    public static <T> ValueValidationResult<T> success(T value) {
        return success(value, Collections.emptyList());
    }

    public static <T> ValueValidationResult<T> success(T value, List<String> warnings) {
        return new ValueValidationResult<>(value, warnings, Collections.emptyList());
    }
}
