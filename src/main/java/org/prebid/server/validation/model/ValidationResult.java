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

    public static ValidationResult error(String errorMessageFormat) {
        return error(Collections.emptyList(), errorMessageFormat);
    }

    public static ValidationResult error(List<String> warnings, String errorMessageFormat) {
        return new ValidationResult(warnings, Collections.singletonList(errorMessageFormat));
    }

    public static ValidationResult success() {
        return success(Collections.emptyList());
    }

    public static ValidationResult success(List<String> warnings) {
        return new ValidationResult(warnings, Collections.emptyList());
    }

    public static ValidationResult warning(List<String> warnings) {
        return new ValidationResult(warnings, Collections.emptyList());
    }
}
