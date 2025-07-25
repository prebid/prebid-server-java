package org.prebid.server.hooks.modules.rule.engine.core.rules.exception;

public class InvalidMatcherConfiguration extends RuntimeException {

    public InvalidMatcherConfiguration(String message) {
        super(message);
    }
}
