package org.prebid.server.validation;

class ValidationException extends Exception {

    ValidationException(String errorMessageFormat) {
        super(errorMessageFormat);
    }

    ValidationException(String errorMessageFormat, Object... args) {
        super(errorMessageFormat.formatted(args));
    }
}
