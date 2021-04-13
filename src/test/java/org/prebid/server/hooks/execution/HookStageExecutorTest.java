package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.EntrypointPayloadImpl;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.HookCatalog;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.Endpoint;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;
import static org.prebid.server.hooks.v1.PayloadUpdate.identity;

@RunWith(VertxUnitRunner.class)
public class HookStageExecutorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HookCatalog hookCatalog;
    private TimeoutFactory timeoutFactory;
    private Vertx vertx;
    private Clock clock;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.systemUTC();
        timeoutFactory = new TimeoutFactory(Clock.fixed(clock.instant(), ZoneOffset.UTC));
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void creationShouldFailWhenExecutionPlanIsInvalid() {
        Assertions.assertThatThrownBy(() -> createExecutor("{endpoints: {abc: {}}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hooks execution plan could not be parsed");
    }

    @Test
    public void shouldTolerateMissingExecutionPlan() {
        // given
        final HookStageExecutor executor = createExecutor(null);

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        final String body = "body";

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                queryParams, headers, body, HookExecutionContext.of(Endpoint.openrtb2_auction));

        // then
        assertThat(future).isSucceeded();

        final EntrypointPayload payload = future.result().getPayload();
        assertThat(payload.queryParams()).isSameAs(queryParams);
        assertThat(payload.headers()).isSameAs(headers);
        assertThat(payload.body()).isSameAs(body);
    }

    @Test
    public void shouldExecuteEntrypointHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-abc"))));

        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                delayedHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-def")), 40));

        givenEntrypointHook(
                "module-beta",
                "hook-a",
                delayedHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-ghi")), 80));

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-jkl"))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.isShouldReject()).isFalse();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body-abc-ghi-jkl-def"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasSize(1)
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();
                                assertThat(groups).hasSize(2);

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks).hasSize(2);

                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(80L, 90L);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks).hasSize(2);

                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(40L, 50L);
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess(150L);
    }

    @Test
    public void shouldBypassEntrypointHooksWhenNoPlanForEndpoint(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(emptyMap()));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_amp);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasSize(1)
                    .containsEntry(Stage.entrypoint, StageExecutionOutcome.of(emptyList()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldBypassEntrypointHooksWhenNoPlanForStage(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(emptyMap()))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasSize(1)
                    .containsEntry(Stage.entrypoint, StageExecutionOutcome.of(emptyList()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksToleratingMisbehavingHooks(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        // unknown hook implementation
        given(hookCatalog.entrypointHookBy(eq("module-alpha"), eq("hook-a")))
                .willReturn(null);

        // hook implementation returns null
        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                (payload, invocationContext) -> null);

        // hook implementation throws exception
        givenEntrypointHook(
                "module-beta",
                "hook-a",
                (payload, invocationContext) -> {
                    throw new RuntimeException("I'm not allowed to throw exceptions");
                });

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-jkl"))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body-jkl"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.invocation_failure);
                                    assertThat(hookOutcome.getMessage())
                                            .isEqualTo("Hook implementation does not exist or disabled");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.invocation_failure);
                                    assertThat(hookOutcome.getMessage()).isEqualTo(
                                            "java.lang.RuntimeException: I'm not allowed to throw exceptions");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks).hasSize(2);

                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.invocation_failure);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Action returned null");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksToleratingTimeoutAndFailedFuture(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        // hook implementation returns future failing after a while
        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                (payload, invocationContext) -> {
                    final Promise<InvocationResult<EntrypointPayload>> promise = Promise.promise();
                    vertx.setTimer(50L, timerId -> promise.fail(new RuntimeException("Failed after a while")));
                    return promise.future();
                });


        // hook implementation takes too long - in async group
        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                delayedHook(
                        InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                                payload.queryParams(), payload.headers(), payload.body() + "-def")),
                        250));

        // hook implementation takes too long - in sync group
        givenEntrypointHook(
                "module-beta",
                "hook-a",
                delayedHook(
                        InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                                payload.queryParams(), payload.headers(), payload.body() + "-ghi")),
                        250));

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-jkl"))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body-jkl"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.execution_failure);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Failed after a while");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(50L, 60L);
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.timeout);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Timed out while executing action");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(100L, 110L);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks).hasSize(2);

                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(0L, 10L);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.timeout);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Timed out while executing action");
                                    assertThat(hookOutcome.getExecutionTime()).isBetween(200L, 210L);
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksHonoringStatusAndAction(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.failed("Failed to contact service ACME")));

        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.noAction()));

        givenEntrypointHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-ghi"))));

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-jkl"))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body-ghi-jkl"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.failure);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Failed to contact service ACME");
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks).hasSize(2);

                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.no_action);
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksWhenRequestIsRejectedByFirstGroup(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-abc"))));

        givenEntrypointHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.rejected("Request is of low quality")));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isTrue();
            assertThat(result.getPayload()).isNull();

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();
                                assertThat(groups).hasSize(1);

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.reject);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Request is of low quality");
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksWhenRequestIsRejectedBySecondGroup(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-abc"))));

        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.rejected("Request is of low quality")));

        givenEntrypointHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-def"))));

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                        payload.queryParams(), payload.headers(), payload.body() + "-jkl"))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isTrue();
            assertThat(result.getPayload()).isNull();

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();
                                assertThat(groups).hasSize(2);

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks).hasSize(2);

                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks).hasSize(2);

                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.reject);
                                    assertThat(hookOutcome.getMessage()).isEqualTo("Request is of low quality");
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksToleratingMisbehavingInvocationResult(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.<EntrypointPayload>builder().build()));

        givenEntrypointHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .build()));

        givenEntrypointHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .build()));

        givenEntrypointHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> {
                    throw new RuntimeException("Can not alter payload");
                })));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.body()).isEqualTo("body"));

            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome -> {
                                final List<GroupExecutionOutcome> groups = stageOutcome.getGroups();

                                final List<HookExecutionOutcome> group0Hooks = groups.get(0).getHooks();
                                assertThat(group0Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isNull();
                                });

                                assertThat(group0Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-a"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                final List<HookExecutionOutcome> group1Hooks = groups.get(1).getHooks();
                                assertThat(group1Hooks.get(0)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-beta", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                });

                                assertThat(group1Hooks.get(1)).satisfies(hookOutcome -> {
                                    assertThat(hookOutcome.getHookId())
                                            .isEqualTo(HookId.of("module-alpha", "hook-b"));
                                    assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                    assertThat(hookOutcome.getAction()).isNull();
                                });
                            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksAndStoreResultInExecutionContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanOneGroupOneHook())))));

        givenEntrypointHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .message("Updated the request")
                        .action(InvocationAction.update)
                        .payloadUpdate(identity())
                        .errors(singletonList("There have been some errors though"))
                        .warnings(singletonList("Not without warnings too"))
                        .debugMessages(singletonList("And chatty debug messages of course"))
                        .build()));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(hookExecutionContext.getStageOutcomes())
                    .hasEntrySatisfying(
                            Stage.entrypoint,
                            stageOutcome ->
                                    assertThat(stageOutcome.getGroups().get(0).getHooks().get(0))
                                            .satisfies(hookOutcome -> {
                                                assertThat(hookOutcome.getHookId())
                                                        .isEqualTo(HookId.of("module-alpha", "hook-a"));
                                                assertThat(hookOutcome.getStatus()).isEqualTo(ExecutionStatus.success);
                                                assertThat(hookOutcome.getMessage()).isEqualTo("Updated the request");
                                                assertThat(hookOutcome.getAction()).isEqualTo(ExecutionAction.update);
                                                assertThat(hookOutcome.getErrors())
                                                        .containsOnly("There have been some errors though");
                                                assertThat(hookOutcome.getWarnings())
                                                        .containsOnly("Not without warnings too");
                                                assertThat(hookOutcome.getDebugMessages())
                                                        .containsOnly("And chatty debug messages of course");
                                            }));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteEntrypointHooksAndPassInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.entrypoint, execPlanTwoGroupsTwoHooksEach())))));

        final EntrypointHookImpl hookImpl = spy(
                EntrypointHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.entrypointHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.entrypointHookBy(eq("module-alpha"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.entrypointHookBy(eq("module-beta"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.entrypointHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                "body",
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<InvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(InvocationContext.class);
            verify(hookImpl, times(4)).call(any(), invocationContextCaptor.capture());
            final List<InvocationContext> capturedContexts = invocationContextCaptor.getAllValues();

            // two invocations for sync group - timeout split between invocations
            assertThat(capturedContexts.get(0)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isEqualTo(Endpoint.openrtb2_auction);
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.timeout().remaining()).isEqualTo(100L);
            });

            assertThat(capturedContexts.get(1)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isEqualTo(Endpoint.openrtb2_auction);
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.timeout().remaining()).isEqualTo(100L);
            });

            // two invocations for async group - timeout the same for both invocations
            assertThat(capturedContexts.get(2)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isEqualTo(Endpoint.openrtb2_auction);
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.timeout().remaining()).isEqualTo(200L);
            });

            assertThat(capturedContexts.get(3)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isEqualTo(Endpoint.openrtb2_auction);
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.timeout().remaining()).isEqualTo(200L);
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    private String executionPlan(Map<Endpoint, EndpointExecutionPlan> endpoints) {
        return jacksonMapper.encode(ExecutionPlan.of(endpoints));
    }


    private static StageExecutionPlan execPlanTwoGroupsTwoHooksEach() {
        return StageExecutionPlan.of(asList(
                ExecutionGroup.of(
                        true,
                        200L,
                        asList(
                                HookId.of("module-alpha", "hook-a"),
                                HookId.of("module-beta", "hook-a"))),
                ExecutionGroup.of(
                        false,
                        200L,
                        asList(
                                HookId.of("module-beta", "hook-b"),
                                HookId.of("module-alpha", "hook-b")))));
    }

    private StageExecutionPlan execPlanOneGroupOneHook() {
        return StageExecutionPlan.of(singletonList(
                ExecutionGroup.of(
                        true,
                        200L,
                        singletonList(HookId.of("module-alpha", "hook-a")))));
    }

    private void givenEntrypointHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<EntrypointPayload, InvocationContext, Future<InvocationResult<EntrypointPayload>>> delegate) {

        given(hookCatalog.entrypointHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(EntrypointHookImpl.of(delegate));
    }

    private <PAYLOAD, CONTEXT> BiFunction<PAYLOAD, CONTEXT, Future<InvocationResult<PAYLOAD>>> delayedHook(
            InvocationResult<PAYLOAD> result,
            int delay) {

        return (payload, context) -> {
            final Promise<InvocationResult<PAYLOAD>> promise = Promise.promise();
            vertx.setTimer(delay, timerId -> promise.complete(result));
            return promise.future();
        };
    }

    private <PAYLOAD, CONTEXT> BiFunction<PAYLOAD, CONTEXT, Future<InvocationResult<PAYLOAD>>> immediateHook(
            InvocationResult<PAYLOAD> result) {

        return (payload, context) -> Future.succeededFuture(result);
    }

    private HookStageExecutor createExecutor(String executionPlan) {
        return HookStageExecutor.create(executionPlan, hookCatalog, timeoutFactory, vertx, clock, jacksonMapper);
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class EntrypointHookImpl implements EntrypointHook {

        String code = "hook-code";

        BiFunction<EntrypointPayload, InvocationContext, Future<InvocationResult<EntrypointPayload>>> delegate;

        @Override
        public Future<InvocationResult<EntrypointPayload>> call(EntrypointPayload entrypointPayload,
                                                                InvocationContext invocationContext) {

            return delegate.apply(entrypointPayload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Accessors(fluent = true)
    @Builder
    @Value
    private static class InvocationResultImpl<PAYLOAD> implements InvocationResult<PAYLOAD> {

        InvocationStatus status;

        String message;

        InvocationAction action;

        PayloadUpdate<PAYLOAD> payloadUpdate;

        List<String> errors;

        List<String> warnings;

        List<String> debugMessages;

        public static <PAYLOAD> InvocationResult<PAYLOAD> succeeded(PayloadUpdate<PAYLOAD> payloadUpdate) {
            return InvocationResultImpl.<PAYLOAD>builder()
                    .status(InvocationStatus.success)
                    .action(InvocationAction.update)
                    .payloadUpdate(payloadUpdate)
                    .build();
        }

        public static <PAYLOAD> InvocationResult<PAYLOAD> failed(String message) {
            return InvocationResultImpl.<PAYLOAD>builder()
                    .status(InvocationStatus.failure)
                    .message(message)
                    .build();
        }

        public static <PAYLOAD> InvocationResult<PAYLOAD> noAction() {
            return InvocationResultImpl.<PAYLOAD>builder()
                    .status(InvocationStatus.success)
                    .action(InvocationAction.no_action)
                    .build();
        }

        public static <PAYLOAD> InvocationResult<PAYLOAD> rejected(String message) {
            return InvocationResultImpl.<PAYLOAD>builder()
                    .status(InvocationStatus.success)
                    .action(InvocationAction.reject)
                    .message(message)
                    .build();
        }
    }
}
