package org.prebid.server.hooks.execution.provider.abtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.hooks.execution.model.ABTest;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.provider.HookProvider;
import org.prebid.server.hooks.execution.v1.LazyHook;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ABTestHookProvider<PAYLOAD, CONTEXT extends InvocationContext> implements HookProvider<PAYLOAD, CONTEXT> {

    private final HookProvider<PAYLOAD, CONTEXT> innerHookProvider;
    private final List<ABTest> abTests;
    private final HookExecutionContext context;
    private final ObjectMapper mapper;

    public ABTestHookProvider(HookProvider<PAYLOAD, CONTEXT> innerHookProvider,
                              List<ABTest> abTests,
                              HookExecutionContext context,
                              ObjectMapper mapper) {

        this.innerHookProvider = Objects.requireNonNull(innerHookProvider);
        this.abTests = Objects.requireNonNull(abTests);
        this.context = Objects.requireNonNull(context);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Hook<PAYLOAD, CONTEXT> apply(HookId hookId) {
        final String moduleCode = hookId.getModuleCode();
        final ABTest abTest = searchForABTest(moduleCode);
        if (abTest == null) {
            return innerHookProvider.apply(hookId);
        }

        return new ABTestHookDecorator<>(
                moduleCode,
                // TODO: if we are staying with "lazy" approach, we need to move HookNotFound checks
                new LazyHook<>(hookId, innerHookProvider),
                shouldInvokeHook(moduleCode, abTest),
                BooleanUtils.isNotFalse(abTest.getLogAnalyticsTag()),
                mapper);
    }

    private ABTest searchForABTest(String moduleCode) {
        return abTests.stream()
                .filter(abTest -> moduleCode.equals(abTest.getModuleCode()))
                .findFirst()
                .orElse(null);
    }

    private boolean shouldInvokeHook(String moduleCode, ABTest abTest) {
        final HookExecutionOutcome hookExecutionOutcome = searchForPreviousExecution(moduleCode);
        if (hookExecutionOutcome != null) {
            return hookExecutionOutcome.getAction() != ExecutionAction.no_invocation;
        }

        final int percent = ObjectUtils.defaultIfNull(abTest.getPercentActive(), 100);
        return ThreadLocalRandom.current().nextInt(100) < percent;
    }

    private HookExecutionOutcome searchForPreviousExecution(String moduleCode) {
        return context.getStageOutcomes().values().stream()
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(StageExecutionOutcome::getGroups)
                .flatMap(Collection::stream)
                .map(GroupExecutionOutcome::getHooks)
                .flatMap(Collection::stream)
                .filter(hookExecutionOutcome -> hookExecutionOutcome.getHookId().getModuleCode().equals(moduleCode))
                .findFirst()
                .orElse(null);
    }
}
