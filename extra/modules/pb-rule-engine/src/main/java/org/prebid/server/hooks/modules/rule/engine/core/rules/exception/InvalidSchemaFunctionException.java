package org.prebid.server.hooks.modules.rule.engine.core.rules.exception;

public class InvalidSchemaFunctionException extends RuntimeException {

    public InvalidSchemaFunctionException(String function) {
        super("Invalid schema function: " + function);
    }
}
