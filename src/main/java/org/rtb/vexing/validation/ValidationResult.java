package org.rtb.vexing.validation;

import java.util.List;

public class ValidationResult {

    private final List<String> errors;

    public ValidationResult(List<String> errors) {
        this.errors = errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }
}
