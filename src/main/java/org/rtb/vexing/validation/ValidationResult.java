package org.rtb.vexing.validation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
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
