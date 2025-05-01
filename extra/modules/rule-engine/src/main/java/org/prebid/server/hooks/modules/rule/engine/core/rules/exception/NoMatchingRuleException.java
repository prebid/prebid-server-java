package org.prebid.server.hooks.modules.rule.engine.core.rules.exception;

public class NoMatchingRuleException extends RuntimeException {

    public NoMatchingRuleException() {
        super("No matching rule found");
    }
}
