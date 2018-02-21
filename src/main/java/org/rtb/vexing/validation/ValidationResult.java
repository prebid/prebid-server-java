package org.rtb.vexing.validation;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Value
public class ValidationResult {

    List<String> errors;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static ValidationResult error(String errorMessageFormat, Object... args) {
        return new ValidationResult(Collections.singletonList(String.format(errorMessageFormat, args)));
    }

    public static ValidationResult success() {
        return new ValidationResult(Collections.emptyList());
    }
}
