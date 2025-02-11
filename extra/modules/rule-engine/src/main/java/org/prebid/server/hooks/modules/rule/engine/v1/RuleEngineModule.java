package org.prebid.server.hooks.modules.rule.engine.v1;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Collections;

public class RuleEngineModule implements Module {

    public static final String CODE = "rule-engine";

    private final Collection<? extends Hook<?, ? extends InvocationContext>> hooks;

    public RuleEngineModule() {
        this.hooks = Collections.singleton(
                new RuleEngineProcessedAuctionRequestHook());
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
