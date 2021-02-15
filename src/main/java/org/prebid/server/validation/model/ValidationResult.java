package org.prebid.server.validation.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
public class ValidationResult {

    List<String> warnings;

    List<String> errors;

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static ValidationResult error(String errorMessageFormat, Object... args) {
        return error(Collections.emptyList(), errorMessageFormat, args);
    }

    public static ValidationResult error(List<String> warnings, String errorMessageFormat, Object... args) {
        return new ValidationResult(warnings, Collections.singletonList(String.format(errorMessageFormat, args)));
    }

    public static ValidationResult success() {
        return success(Collections.emptyList());
    }

    public static ValidationResult success(List<String> warnings) {
        return new ValidationResult(warnings, Collections.emptyList());
    }
}
