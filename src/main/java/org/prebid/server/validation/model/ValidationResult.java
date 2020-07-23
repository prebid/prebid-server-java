package org.prebid.server.validation.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Value
public class ValidationResult {

    List<String> errors;

    List<String> warnings;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public static ValidationResult error(String errorMessageFormat, Object... args) {
        return new ValidationResult(Collections.singletonList(String.format(errorMessageFormat, args)),
                Collections.emptyList());
    }

    public static ValidationResult success() {
        return new ValidationResult(Collections.emptyList(), Collections.emptyList());
    }

    public static ValidationResult warning(List<String> warnings) {
        return new ValidationResult(Collections.emptyList(), warnings);
    }
}
