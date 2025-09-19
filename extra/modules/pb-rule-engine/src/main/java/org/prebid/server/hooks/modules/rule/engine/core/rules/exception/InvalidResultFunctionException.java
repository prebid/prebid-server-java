package org.prebid.server.hooks.modules.rule.engine.core.rules.exception;

public class InvalidResultFunctionException extends RuntimeException {

    public InvalidResultFunctionException(String function) {
        super("Invalid result function: " + function);
    }
}
