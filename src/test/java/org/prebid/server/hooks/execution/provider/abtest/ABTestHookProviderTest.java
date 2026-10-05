package org.prebid.server.hooks.execution.provider.abtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.VertxTest;
import org.prebid.server.hooks.execution.model.ABTest;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.provider.HookProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ABTestHookProviderTest extends VertxTest {

    @Mock
    private HookProvider<Object, InvocationContext> innerHookProvider;

    @Mock
    private Hook<Object, InvocationContext> innerHook;

    @BeforeEach
    public void setUp() {
        given(innerHookProvider.apply(any())).willReturn(innerHook);
    }

    @Test
    public void applyShouldReturnOriginalHookIfNoABTestFound() {
        // given
        final HookProvider<Object, InvocationContext> target = new ABTestHookProvider<>(
                innerHookProvider,
                singletonList(ABTest.builder().moduleCode("otherModule").build()),
                HookExecutionContext.of(HookHttpEndpoint.POST_AUCTION),
                mapper);

        // when
        final Hook<Object, InvocationContext> result = target.apply(hookId());

        // then
        verify(innerHookProvider).apply(any());
        verifyNoInteractions(innerHook);
        assertThat(result).isSameAs(innerHook);
    }

    @Test
    public void applyShouldReturnWrappedHook() {
        // given
        final HookProvider<Object, InvocationContext> target = new ABTestHookProvider<>(
                innerHookProvider,
                singletonList(ABTest.builder().moduleCode("module").build()),
                HookExecutionContext.of(HookHttpEndpoint.POST_AUCTION),
                mapper);

        // when
        final Hook<Object, InvocationContext> result = target.apply(hookId());

        // then
        verify(innerHookProvider).apply(any());
        verifyNoInteractions(innerHook);
        assertThat(result).isInstanceOf(ABTestHook.class);
    }

    @Test
    public void shouldInvokeHookShouldReturnTrueIfThereIsAPreviousInvocation() {
        // given
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(HookHttpEndpoint.POST_AUCTION);
        hookExecutionContext.getStageOutcomes().put(Stage.entrypoint, singletonList(
                StageExecutionOutcome.of("entity", singletonList(GroupExecutionOutcome.of(singletonList(
                        HookExecutionOutcome.builder()
                                .hookId(hookId())
                                .action(ExecutionAction.update)
                                .build()))))));

        final ABTestHookProvider<Object, InvocationContext> target = new ABTestHookProvider<>(
                innerHookProvider,
                emptyList(),
                hookExecutionContext,
                mapper);

        // when and then
        verifyNoInteractions(innerHookProvider);
        verifyNoInteractions(innerHook);
        assertThat(target.shouldInvokeHook("module", null)).isTrue();
    }

    @Test
    public void shouldInvokeHookShouldReturnFalseIfThereIsAPreviousExecutionWithoutInvocation() {
        // given
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(HookHttpEndpoint.POST_AUCTION);
        hookExecutionContext.getStageOutcomes().put(Stage.entrypoint, singletonList(
                StageExecutionOutcome.of("entity", singletonList(GroupExecutionOutcome.of(singletonList(
                        HookExecutionOutcome.builder()
                                .hookId(hookId())
                                .action(ExecutionAction.no_invocation)
                                .build()))))));

        final ABTestHookProvider<Object, InvocationContext> target = new ABTestHookProvider<>(
                innerHookProvider,
                emptyList(),
                hookExecutionContext,
                mapper);

        // when and then
        verifyNoInteractions(innerHookProvider);
        verifyNoInteractions(innerHook);
        assertThat(target.shouldInvokeHook("module", null)).isFalse();
    }

    private static HookId hookId() {
        return HookId.of("module", "hook");
    }
}
