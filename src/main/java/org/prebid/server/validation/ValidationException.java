package org.prebid.server.validation;

class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    ValidationException(String errorMessageFormat, Object... args) {
        super(String.format(errorMessageFormat, args));
    }
}
