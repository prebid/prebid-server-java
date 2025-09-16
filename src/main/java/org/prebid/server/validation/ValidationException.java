package org.prebid.server.validation;

public class ValidationException extends Exception {

    public ValidationException(String errorMessageFormat) {
        super(errorMessageFormat);
    }

    public ValidationException(String errorMessageFormat, Object... args) {
        super(errorMessageFormat.formatted(args));
    }
}
