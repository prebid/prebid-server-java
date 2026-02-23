package org.prebid.server.hooks.modules.rule.engine.core.util;

public class NoMatchingValueException extends RuntimeException {

    public NoMatchingValueException(String message) {
        super(message);
    }
}
