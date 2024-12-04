package org.prebid.server.hooks.execution.provider.abtest;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultUtils;
import org.prebid.server.hooks.v1.InvocationStatus;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.hooks.v1.PayloadUpdate.identity;

@ExtendWith(MockitoExtension.class)
public class ABTestHookTest extends VertxTest {

    @Mock
    private Hook<Object, InvocationContext> innerHook;

    @Mock
    private Object payload;

    @Mock
    private InvocationContext invocationContext;

    @Test
    public void codeShouldReturnSameHookCode() {
        // given
        given(innerHook.code()).willReturn("code");

        final Hook<Object, InvocationContext> target = new ABTestHook<>(
                "module",
                innerHook,
                false,
                false,
                mapper);

        // when and then
        assertThat(target.code()).isEqualTo("code");
    }

    @Test
    public void callShouldReturnSkippedResultWithoutTags() {
        // given
        final Hook<Object, InvocationContext> target = new ABTestHook<>(
                "module",
                innerHook,
                false,
                false,
                mapper);

        // when
        final InvocationResult<Object> invocationResult = target.call(payload, invocationContext).result();

        // then
        assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(invocationResult.analyticsTags()).isNull();
    }

    @Test
    public void callShouldReturnSkippedResultWithTags() {
        // given
        final Hook<Object, InvocationContext> target = new ABTestHook<>(
                "module",
                innerHook,
                false,
                true,
                mapper);

        // when
        final InvocationResult<Object> invocationResult = target.call(payload, invocationContext).result();

        // then
        assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_invocation);
        assertThat(invocationResult.analyticsTags().activities()).hasSize(1).allSatisfy(activity -> {
            assertThat(activity.name()).isEqualTo("core-module-abtests");
            assertThat(activity.status()).isEqualTo("success");
            assertThat(activity.results()).hasSize(1).allSatisfy(result -> {
                assertThat(result.status()).isEqualTo("skipped");
                assertThat(result.values()).isEqualTo(mapper.createObjectNode().put("module", "module"));
            });
        });
    }

    @Test
    public void callShouldReturnRunResultWithoutTags() {
        // given
        final Hook<Object, InvocationContext> target = new ABTestHook<>(
                "module",
                innerHook,
                true,
                false,
                mapper);

        final InvocationResult<Object> innerHookInvocationResult = spy(InvocationResultUtils.succeeded(identity()));
        final Future<InvocationResult<Object>> innerHookResult = Future.succeededFuture(innerHookInvocationResult);
        given(innerHook.call(any(), any())).willReturn(innerHookResult);

        // when
        final Future<InvocationResult<Object>> result = target.call(payload, invocationContext);

        // then
        verify(innerHook).call(same(payload), same(invocationContext));
        verifyNoInteractions(innerHookInvocationResult);
        assertThat(result).isSameAs(innerHookResult);
    }

    @Test
    public void callShouldReturnRunResultWithTags() {
        // given
        final Hook<Object, InvocationContext> target = new ABTestHook<>(
                "module",
                innerHook,
                true,
                true,
                mapper);

        final InvocationResult<Object> innerHookInvocationResult = spy(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .message("message")
                .action(InvocationAction.update)
                .payloadUpdate(identity())
                .errors(singletonList("error"))
                .warnings(singletonList("warning"))
                .debugMessages(singletonList("debugMessages"))
                .moduleContext(new Object())
                .analyticsTags(TagsImpl.of(asList(
                        ActivityImpl.of("activity0", null, null),
                        ActivityImpl.of("activity1", null, null))))
                .build());
        given(innerHook.call(any(), any())).willReturn(Future.succeededFuture(innerHookInvocationResult));

        // when
        final Future<InvocationResult<Object>> result = target.call(payload, invocationContext);

        // then
        verify(innerHook).call(same(payload), same(invocationContext));
        verifyNoInteractions(innerHookInvocationResult);

        final InvocationResult<Object> invocationResult = result.result();
        assertThat(invocationResult.status()).isSameAs(innerHookInvocationResult.status());
        assertThat(invocationResult.message()).isSameAs(innerHookInvocationResult.message());
        assertThat(invocationResult.action()).isSameAs(innerHookInvocationResult.action());
        assertThat(invocationResult.payloadUpdate()).isSameAs(innerHookInvocationResult.payloadUpdate());
        assertThat(invocationResult.errors()).isSameAs(innerHookInvocationResult.errors());
        assertThat(invocationResult.warnings()).isSameAs(innerHookInvocationResult.warnings());
        assertThat(invocationResult.debugMessages()).isSameAs(innerHookInvocationResult.debugMessages());
        assertThat(invocationResult.moduleContext()).isSameAs(innerHookInvocationResult.moduleContext());
        assertThat(invocationResult.analyticsTags().activities()).satisfies(activities -> {
            for (int i = 0; i < activities.size() - 1; i++) {
                assertThat(activities.get(i)).isSameAs(innerHookInvocationResult.analyticsTags().activities().get(i));
            }

            assertThat(activities.getLast()).satisfies(activity -> {
                assertThat(activity.name()).isEqualTo("core-module-abtests");
                assertThat(activity.status()).isEqualTo("success");
                assertThat(activity.results()).hasSize(1).allSatisfy(activityResult -> {
                    assertThat(activityResult.status()).isEqualTo("run");
                    assertThat(activityResult.values())
                            .isEqualTo(mapper.createObjectNode().put("module", "module"));
                });
            });
        });
    }
}
