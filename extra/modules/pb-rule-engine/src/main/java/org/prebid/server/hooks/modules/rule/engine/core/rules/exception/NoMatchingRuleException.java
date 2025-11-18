package org.prebid.server.hooks.modules.rule.engine.core.rules.exception;

import org.prebid.server.hooks.modules.rule.engine.core.util.NoMatchingValueException;

public class NoMatchingRuleException extends NoMatchingValueException {

    public NoMatchingRuleException() {
        super("No matching rule found");
    }
}
