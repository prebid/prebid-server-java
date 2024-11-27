package org.prebid.server.hooks.execution.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.hooks.execution.HookCatalog;
import org.prebid.server.hooks.execution.model.ABTest;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.execution.provider.abtest.ABTestHookProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.List;
import java.util.Objects;

public class HookProviderFactory {

    private final HookCatalog hookCatalog;
    private final ObjectMapper mapper;

    public HookProviderFactory(HookCatalog hookCatalog, ObjectMapper mapper) {
        this.hookCatalog = Objects.requireNonNull(hookCatalog);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public <PAYLOAD, CONTEXT extends InvocationContext>
            HookProviderBuilder<PAYLOAD, CONTEXT> builderForStage(
                    StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage) {

        return new HookProviderBuilder<>(stage);
    }

    public class HookProviderBuilder<PAYLOAD, CONTEXT extends InvocationContext> {

        private HookProvider<PAYLOAD, CONTEXT> hookProvider;

        public HookProviderBuilder(StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage) {
            this.hookProvider = hookId -> hookCatalog.hookById(hookId, stage);
        }

        public HookProviderBuilder<PAYLOAD, CONTEXT> decorateWithABTest(List<ABTest> abTests,
                                                                        HookExecutionContext context) {

            this.hookProvider = new ABTestHookProvider<>(this.hookProvider, abTests, context, mapper);
            return this;
        }

        public HookProvider<PAYLOAD, CONTEXT> build() {
            return hookProvider;
        }
    }
}
