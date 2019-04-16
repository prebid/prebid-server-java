package org.prebid.server.validation;

@SuppressWarnings("serial")
class ValidationException extends Exception {

    ValidationException(String errorMessageFormat, Object... args) {
        super(String.format(errorMessageFormat, args));
    }
}
