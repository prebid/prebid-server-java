package org.prebid.server.hooks.modules.rule.engine.v1;

import org.prebid.server.hooks.modules.rule.engine.core.config.cache.RuleParser;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Collections;

public class RuleEngineModule implements Module {

    public static final String CODE = "rule-engine";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public RuleEngineModule(RuleParser ruleParser) {
        this.hooks = Collections.singleton(
                new RuleEngineProcessedAuctionRequestHook(ruleParser));
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return hooks;
    }
}
