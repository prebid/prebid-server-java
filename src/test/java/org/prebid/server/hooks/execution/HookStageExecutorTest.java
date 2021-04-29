package org.prebid.server.hooks.execution;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
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
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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
    public void shouldTolerateMissingDefaultExecutionPlan() {
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
    public void shouldTolerateMissingDefaultAndAccountExecutionPlan() {
        // given
        final HookStageExecutor executor = createExecutor(null);

        // when
        final BidRequest bidRequest = BidRequest.builder().build();
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                bidRequest,
                Account.empty("accountId"),
                HookExecutionContext.of(Endpoint.openrtb2_auction));

        // then
        assertThat(future).isSucceeded();

        final AuctionRequestPayload payload = future.result().getPayload();
        assertThat(payload.bidRequest()).isSameAs(bidRequest);
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

    @Test
    public void shouldExecuteRawAuctionRequestHooksWhenNoExecutionPlanInAccount(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.raw_auction_request, execPlanOneGroupOneHook())))));

        final RawAuctionRequestHookImpl hookImpl = spy(
                RawAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.noAction())));
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);

        final BidRequest bidRequest = BidRequest.builder().build();
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                bidRequest,
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isSameAs(bidRequest));

            verify(hookImpl).call(any(), any());

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawAuctionRequestHooksWhenAccountOverridesExecutionPlan(TestContext context) {
        // given
        final String defaultPlan = executionPlan(singletonMap(
                Endpoint.openrtb2_auction,
                EndpointExecutionPlan.of(singletonMap(
                        Stage.raw_auction_request,
                        StageExecutionPlan.of(singletonList(
                                ExecutionGroup.of(
                                        true,
                                        200L,
                                        singletonList(HookId.of("module-alpha", "hook-a")))))))));
        final HookStageExecutor executor = createExecutor(defaultPlan);

        final RawAuctionRequestHookImpl hookImpl = spy(
                RawAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.noAction())));
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);

        final BidRequest bidRequest = BidRequest.builder().build();
        final ExecutionPlan accountPlan = ExecutionPlan.of(singletonMap(
                Endpoint.openrtb2_auction,
                EndpointExecutionPlan.of(singletonMap(
                        Stage.raw_auction_request,
                        StageExecutionPlan.of(singletonList(
                                ExecutionGroup.of(
                                        true,
                                        200L,
                                        singletonList(HookId.of("module-beta", "hook-b")))))))));
        final Account account = Account.builder()
                .id("accountId")
                .hooks(AccountHooksConfiguration.of(accountPlan, null))
                .build();
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                bidRequest,
                account,
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isSameAs(bidRequest));

            verify(hookImpl).call(any(), any());

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawAuctionRequestHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.raw_auction_request,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenRawAuctionRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().at(1).build()))));

        givenRawAuctionRequestHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().id("id").build()))));

        givenRawAuctionRequestHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().test(1).build()))));

        givenRawAuctionRequestHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().tmax(1000L).build()))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isEqualTo(BidRequest.builder()
                            .at(1)
                            .id("id")
                            .test(1)
                            .tmax(1000L)
                            .build()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawAuctionRequestHooksAndPassAuctionInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.raw_auction_request,
                                execPlanTwoGroupsTwoHooksEach())))));

        final RawAuctionRequestHookImpl hookImpl = spy(
                RawAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);

        final Map<String, ObjectNode> accountModulesConfiguration = new HashMap<>();
        final ObjectNode moduleAlphaConfiguration = mapper.createObjectNode();
        final ObjectNode moduleBetaConfiguration = mapper.createObjectNode();
        accountModulesConfiguration.put("module-alpha", moduleAlphaConfiguration);
        accountModulesConfiguration.put("module-beta", moduleBetaConfiguration);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                BidRequest.builder().build(),
                Account.builder()
                        .hooks(AccountHooksConfiguration.of(null, accountModulesConfiguration))
                        .build(),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<AuctionInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(AuctionInvocationContext.class);
            verify(hookImpl, times(4)).call(any(), invocationContextCaptor.capture());
            final List<AuctionInvocationContext> capturedContexts = invocationContextCaptor.getAllValues();

            assertThat(capturedContexts.get(0)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleAlphaConfiguration);
            });

            assertThat(capturedContexts.get(1)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleBetaConfiguration);
            });

            assertThat(capturedContexts.get(2)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleBetaConfiguration);
            });

            assertThat(capturedContexts.get(3)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleAlphaConfiguration);
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawAuctionRequestHooksAndPassModuleContextBetweenHooks(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.raw_auction_request,
                                StageExecutionPlan.of(asList(
                                        ExecutionGroup.of(
                                                false,
                                                200L,
                                                asList(
                                                        HookId.of("module-alpha", "hook-a"),
                                                        HookId.of("module-beta", "hook-a"),
                                                        HookId.of("module-alpha", "hook-c"))),
                                        ExecutionGroup.of(
                                                true,
                                                200L,
                                                asList(
                                                        HookId.of("module-beta", "hook-b"),
                                                        HookId.of("module-alpha", "hook-b"),
                                                        HookId.of("module-beta", "hook-c"))))))))));

        final RawAuctionRequestHookImpl hookImpl = spy(RawAuctionRequestHookImpl.of(
                (payload, invocationContext) -> {
                    final Promise<InvocationResult<AuctionRequestPayload>> promise = Promise.promise();
                    vertx.setTimer(20, timerId -> promise.complete(
                            InvocationResultImpl.<AuctionRequestPayload>builder()
                                    .status(InvocationStatus.success)
                                    .action(InvocationAction.update)
                                    .payloadUpdate(identity())
                                    .moduleContext(
                                            StringUtils.trimToEmpty((String) invocationContext.moduleContext()) + "a")
                                    .build()));
                    return promise.future();
                }));
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-alpha"), eq("hook-c"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.rawAuctionRequestHookBy(eq("module-beta"), eq("hook-c"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<AuctionInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(AuctionInvocationContext.class);
            verify(hookImpl, times(6)).call(any(), invocationContextCaptor.capture());
            final List<AuctionInvocationContext> capturedContexts = invocationContextCaptor.getAllValues();

            assertThat(capturedContexts.get(0)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(1)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(2)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(3)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("a"));

            assertThat(capturedContexts.get(4)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("a"));

            assertThat(capturedContexts.get(5)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("aa"));

            assertThat(hookExecutionContext.getModuleContexts()).containsOnly(
                    entry("module-alpha", "aa"),
                    entry("module-beta", "aaa"));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawAuctionRequestHooksWhenRequestIsRejected(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.raw_auction_request, execPlanOneGroupOneHook())))));

        givenRawAuctionRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.rejected("Request is no good")));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future = executor.executeRawAuctionRequestStage(
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isTrue();
            assertThat(result.getPayload()).isNull();

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksWhenNoExecutionPlanInAccount(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_auction_request,
                                execPlanOneGroupOneHook())))));

        final ProcessedAuctionRequestHookImpl hookImpl = spy(
                ProcessedAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.noAction())));
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-a")))
                .willReturn(hookImpl);

        final BidRequest bidRequest = BidRequest.builder().build();
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        bidRequest,
                        Account.empty("accountId"),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isSameAs(bidRequest));

            verify(hookImpl).call(any(), any());

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksWhenAccountOverridesExecutionPlan(TestContext context) {
        // given
        final String defaultPlan = executionPlan(singletonMap(
                Endpoint.openrtb2_auction,
                EndpointExecutionPlan.of(singletonMap(
                        Stage.processed_auction_request,
                        StageExecutionPlan.of(singletonList(
                                ExecutionGroup.of(
                                        true,
                                        200L,
                                        singletonList(HookId.of("module-alpha", "hook-a")))))))));
        final HookStageExecutor executor = createExecutor(defaultPlan);

        final ProcessedAuctionRequestHookImpl hookImpl = spy(
                ProcessedAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.noAction())));
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-b")))
                .willReturn(hookImpl);

        final BidRequest bidRequest = BidRequest.builder().build();
        final ExecutionPlan accountPlan = ExecutionPlan.of(singletonMap(
                Endpoint.openrtb2_auction,
                EndpointExecutionPlan.of(singletonMap(
                        Stage.processed_auction_request,
                        StageExecutionPlan.of(singletonList(
                                ExecutionGroup.of(
                                        true,
                                        200L,
                                        singletonList(HookId.of("module-beta", "hook-b")))))))));
        final Account account = Account.builder()
                .id("accountId")
                .hooks(AccountHooksConfiguration.of(accountPlan, null))
                .build();
        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        bidRequest,
                        account,
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isSameAs(bidRequest));

            verify(hookImpl).call(any(), any());

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_auction_request,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenProcessedAuctionRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().at(1).build()))));

        givenProcessedAuctionRequestHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().id("id").build()))));

        givenProcessedAuctionRequestHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().test(1).build()))));

        givenProcessedAuctionRequestHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().tmax(1000L).build()))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        BidRequest.builder().build(),
                        Account.empty("accountId"),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isEqualTo(BidRequest.builder()
                            .at(1)
                            .id("id")
                            .test(1)
                            .tmax(1000L)
                            .build()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksAndPassAuctionInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_auction_request,
                                execPlanTwoGroupsTwoHooksEach())))));

        final ProcessedAuctionRequestHookImpl hookImpl = spy(
                ProcessedAuctionRequestHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);

        final Map<String, ObjectNode> accountModulesConfiguration = new HashMap<>();
        final ObjectNode moduleAlphaConfiguration = mapper.createObjectNode();
        final ObjectNode moduleBetaConfiguration = mapper.createObjectNode();
        accountModulesConfiguration.put("module-alpha", moduleAlphaConfiguration);
        accountModulesConfiguration.put("module-beta", moduleBetaConfiguration);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        BidRequest.builder().build(),
                        Account.builder()
                                .hooks(AccountHooksConfiguration.of(null, accountModulesConfiguration))
                                .build(),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<AuctionInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(AuctionInvocationContext.class);
            verify(hookImpl, times(4)).call(any(), invocationContextCaptor.capture());
            final List<AuctionInvocationContext> capturedContexts = invocationContextCaptor.getAllValues();

            assertThat(capturedContexts.get(0)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleAlphaConfiguration);
            });

            assertThat(capturedContexts.get(1)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleBetaConfiguration);
            });

            assertThat(capturedContexts.get(2)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleBetaConfiguration);
            });

            assertThat(capturedContexts.get(3)).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.debugEnabled()).isFalse();
                assertThat(invocationContext.accountConfig()).isSameAs(moduleAlphaConfiguration);
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksAndPassModuleContextBetweenHooks(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_auction_request,
                                StageExecutionPlan.of(asList(
                                        ExecutionGroup.of(
                                                false,
                                                200L,
                                                asList(
                                                        HookId.of("module-alpha", "hook-a"),
                                                        HookId.of("module-beta", "hook-a"),
                                                        HookId.of("module-alpha", "hook-c"))),
                                        ExecutionGroup.of(
                                                true,
                                                200L,
                                                asList(
                                                        HookId.of("module-beta", "hook-b"),
                                                        HookId.of("module-alpha", "hook-b"),
                                                        HookId.of("module-beta", "hook-c"))))))))));

        final ProcessedAuctionRequestHookImpl hookImpl = spy(ProcessedAuctionRequestHookImpl.of(
                (payload, invocationContext) -> {
                    final Promise<InvocationResult<AuctionRequestPayload>> promise = Promise.promise();
                    vertx.setTimer(20, timerId -> promise.complete(
                            InvocationResultImpl.<AuctionRequestPayload>builder()
                                    .status(InvocationStatus.success)
                                    .action(InvocationAction.update)
                                    .payloadUpdate(identity())
                                    .moduleContext(
                                            StringUtils.trimToEmpty((String) invocationContext.moduleContext()) + "a")
                                    .build()));
                    return promise.future();
                }));
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-alpha"), eq("hook-c"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-a"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-b"))).willReturn(hookImpl);
        given(hookCatalog.processedAuctionRequestHookBy(eq("module-beta"), eq("hook-c"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        BidRequest.builder().build(),
                        Account.empty("accountId"),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<AuctionInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(AuctionInvocationContext.class);
            verify(hookImpl, times(6)).call(any(), invocationContextCaptor.capture());
            final List<AuctionInvocationContext> capturedContexts = invocationContextCaptor.getAllValues();

            assertThat(capturedContexts.get(0)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(1)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(2)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isNull());

            assertThat(capturedContexts.get(3)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("a"));

            assertThat(capturedContexts.get(4)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("a"));

            assertThat(capturedContexts.get(5)).satisfies(invocationContext ->
                    assertThat(invocationContext.moduleContext()).isEqualTo("aa"));

            assertThat(hookExecutionContext.getModuleContexts()).containsOnly(
                    entry("module-alpha", "aa"),
                    entry("module-beta", "aaa"));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedAuctionRequestHooksWhenRequestIsRejected(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_auction_request,
                                execPlanOneGroupOneHook())))));

        givenProcessedAuctionRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.rejected("Request is no good")));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionRequestPayload>> future =
                executor.executeProcessedAuctionRequestStage(
                        BidRequest.builder().build(),
                        Account.empty("accountId"),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isTrue();
            assertThat(result.getPayload()).isNull();

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteBidderRequestHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.bidder_request,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenBidderRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().at(1).build()))));

        givenBidderRequestHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().id("id").build()))));

        givenBidderRequestHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().test(1).build()))));

        givenBidderRequestHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderRequestPayloadImpl.of(
                        payload.bidRequest().toBuilder().tmax(1000L).build()))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderRequestPayload>> future = executor.executeBidderRequestStage(
                BidderRequest.of("bidder1", null, BidRequest.builder().build()),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidRequest()).isEqualTo(BidRequest.builder()
                            .at(1)
                            .id("id")
                            .test(1)
                            .tmax(1000L)
                            .build()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteBidderRequestHooksAndPassBidderInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.bidder_request, execPlanOneGroupOneHook())))));

        final BidderRequestHookImpl hookImpl = spy(
                BidderRequestHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.bidderRequestHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderRequestPayload>> future = executor.executeBidderRequestStage(
                BidderRequest.of("bidder1", null, BidRequest.builder().build()),
                Account.builder()
                        .hooks(AccountHooksConfiguration.of(
                                null, singletonMap("module-alpha", mapper.createObjectNode())))
                        .build(),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<BidderInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(BidderInvocationContext.class);
            verify(hookImpl).call(any(), invocationContextCaptor.capture());

            assertThat(invocationContextCaptor.getValue()).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.accountConfig()).isNotNull();
                assertThat(invocationContext.bidder()).isEqualTo("bidder1");
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawBidderResponseHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.raw_bidder_response,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenRawBidderResponseHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().id("bidId").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenRawBidderResponseHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().adid("adId").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenRawBidderResponseHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().cid("cid").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenRawBidderResponseHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().adm("adm").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderResponsePayload>> future = executor.executeRawBidderResponseStage(
                BidderResponse.of(
                        "bidder1",
                        BidderSeatBid.of(
                                singletonList(BidderBid.of(Bid.builder().build(), BidType.banner, "USD")),
                                emptyList(),
                                emptyList()),
                        0),
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bids()).containsOnly(BidderBid.of(
                            Bid.builder()
                                    .id("bidId")
                                    .adid("adId")
                                    .cid("cid")
                                    .adm("adm")
                                    .build(),
                            BidType.banner,
                            "USD")));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteRawBidderResponseHooksAndPassBidderInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.raw_bidder_response, execPlanOneGroupOneHook())))));

        final RawBidderResponseHookImpl hookImpl = spy(
                RawBidderResponseHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.rawBidderResponseHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderResponsePayload>> future = executor.executeRawBidderResponseStage(
                BidderResponse.of(
                        "bidder1",
                        BidderSeatBid.of(
                                singletonList(BidderBid.of(Bid.builder().build(), BidType.banner, "USD")),
                                emptyList(),
                                emptyList()),
                        0),
                BidRequest.builder().build(),
                Account.builder()
                        .hooks(AccountHooksConfiguration.of(
                                null, singletonMap("module-alpha", mapper.createObjectNode())))
                        .build(),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<BidderInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(BidderInvocationContext.class);
            verify(hookImpl).call(any(), invocationContextCaptor.capture());

            assertThat(invocationContextCaptor.getValue()).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.accountConfig()).isNotNull();
                assertThat(invocationContext.bidder()).isEqualTo("bidder1");
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedBidderResponseHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_bidder_response,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenProcessedBidderResponseHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().id("bidId").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenProcessedBidderResponseHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().adid("adId").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenProcessedBidderResponseHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().cid("cid").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        givenProcessedBidderResponseHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> BidderResponsePayloadImpl.of(
                        payload.bids().stream()
                                .map(bid -> BidderBid.of(
                                        bid.getBid().toBuilder().adm("adm").build(),
                                        bid.getType(),
                                        bid.getBidCurrency()))
                                .collect(Collectors.toList())))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderResponsePayload>> future =
                executor.executeProcessedBidderResponseStage(
                        singletonList(BidderBid.of(Bid.builder().build(), BidType.banner, "USD")),
                        "bidder1",
                        BidRequest.builder().build(),
                        Account.empty("accountId"),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bids()).containsOnly(BidderBid.of(
                            Bid.builder()
                                    .id("bidId")
                                    .adid("adId")
                                    .cid("cid")
                                    .adm("adm")
                                    .build(),
                            BidType.banner,
                            "USD")));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteProcessedBidderResponseHooksAndPassBidderInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.processed_bidder_response,
                                execPlanOneGroupOneHook())))));

        final ProcessedBidderResponseHookImpl hookImpl = spy(
                ProcessedBidderResponseHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.processedBidderResponseHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderResponsePayload>> future =
                executor.executeProcessedBidderResponseStage(
                        singletonList(BidderBid.of(Bid.builder().build(), BidType.banner, "USD")),
                        "bidder1",
                        BidRequest.builder().build(),
                        Account.builder()
                                .hooks(AccountHooksConfiguration.of(
                                        null, singletonMap("module-alpha", mapper.createObjectNode())))
                                .build(),
                        hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<BidderInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(BidderInvocationContext.class);
            verify(hookImpl).call(any(), invocationContextCaptor.capture());

            assertThat(invocationContextCaptor.getValue()).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.accountConfig()).isNotNull();
                assertThat(invocationContext.bidder()).isEqualTo("bidder1");
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteBidderRequestHooksWhenRequestIsRejected(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.bidder_request, execPlanOneGroupOneHook())))));

        givenBidderRequestHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.rejected("Request is no good")));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<BidderRequestPayload>> future = executor.executeBidderRequestStage(
                BidderRequest.of("bidder1", null, BidRequest.builder().build()),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isTrue();
            assertThat(result.getPayload()).isNull();

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteAuctionResponseHooksHappyPath(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(
                                Stage.auction_response,
                                execPlanTwoGroupsTwoHooksEach())))));

        givenAuctionResponseHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionResponsePayloadImpl.of(
                        payload.bidResponse().toBuilder().id("id").build()))));

        givenAuctionResponseHook(
                "module-alpha",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionResponsePayloadImpl.of(
                        payload.bidResponse().toBuilder().bidid("bidid").build()))));

        givenAuctionResponseHook(
                "module-beta",
                "hook-a",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionResponsePayloadImpl.of(
                        payload.bidResponse().toBuilder().cur("cur").build()))));

        givenAuctionResponseHook(
                "module-beta",
                "hook-b",
                immediateHook(InvocationResultImpl.succeeded(payload -> AuctionResponsePayloadImpl.of(
                        payload.bidResponse().toBuilder().nbr(1).build()))));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionResponsePayload>> future = executor.executeAuctionResponseStage(
                BidResponse.builder().build(),
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidResponse()).isEqualTo(BidResponse.builder()
                            .id("id")
                            .bidid("bidid")
                            .cur("cur")
                            .nbr(1)
                            .build()));

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteAuctionResponseHooksAndPassAuctionInvocationContext(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.auction_response, execPlanOneGroupOneHook())))));

        final AuctionResponseHookImpl hookImpl = spy(
                AuctionResponseHookImpl.of(immediateHook(InvocationResultImpl.succeeded(identity()))));
        given(hookCatalog.auctionResponseHookBy(eq("module-alpha"), eq("hook-a"))).willReturn(hookImpl);

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionResponsePayload>> future = executor.executeAuctionResponseStage(
                BidResponse.builder().build(),
                BidRequest.builder().build(),
                Account.builder()
                        .hooks(AccountHooksConfiguration.of(
                                null, singletonMap("module-alpha", mapper.createObjectNode())))
                        .build(),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            final ArgumentCaptor<AuctionInvocationContext> invocationContextCaptor =
                    ArgumentCaptor.forClass(AuctionInvocationContext.class);
            verify(hookImpl).call(any(), invocationContextCaptor.capture());

            assertThat(invocationContextCaptor.getValue()).satisfies(invocationContext -> {
                assertThat(invocationContext.endpoint()).isNotNull();
                assertThat(invocationContext.timeout()).isNotNull();
                assertThat(invocationContext.accountConfig()).isNotNull();
            });

            async.complete();
        }));

        async.awaitSuccess();
    }

    @Test
    public void shouldExecuteAuctionResponseHooksAndIgnoreRejection(TestContext context) {
        // given
        final HookStageExecutor executor = createExecutor(
                executionPlan(singletonMap(
                        Endpoint.openrtb2_auction,
                        EndpointExecutionPlan.of(singletonMap(Stage.auction_response, execPlanOneGroupOneHook())))));

        givenAuctionResponseHook(
                "module-alpha",
                "hook-a",
                immediateHook(InvocationResultImpl.rejected("Will not apply")));

        final HookExecutionContext hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        // when
        final Future<HookStageExecutionResult<AuctionResponsePayload>> future = executor.executeAuctionResponseStage(
                BidResponse.builder().build(),
                BidRequest.builder().build(),
                Account.empty("accountId"),
                hookExecutionContext);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result.isShouldReject()).isFalse();
            assertThat(result.getPayload()).isNotNull().satisfies(payload ->
                    assertThat(payload.bidResponse()).isNotNull());

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

    private void givenRawAuctionRequestHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    AuctionRequestPayload,
                    AuctionInvocationContext,
                    Future<InvocationResult<AuctionRequestPayload>>> delegate) {

        given(hookCatalog.rawAuctionRequestHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(RawAuctionRequestHookImpl.of(delegate));
    }

    private void givenProcessedAuctionRequestHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    AuctionRequestPayload,
                    AuctionInvocationContext,
                    Future<InvocationResult<AuctionRequestPayload>>> delegate) {

        given(hookCatalog.processedAuctionRequestHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(ProcessedAuctionRequestHookImpl.of(delegate));
    }

    private void givenBidderRequestHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    BidderRequestPayload,
                    BidderInvocationContext,
                    Future<InvocationResult<BidderRequestPayload>>> delegate) {

        given(hookCatalog.bidderRequestHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(BidderRequestHookImpl.of(delegate));
    }

    private void givenRawBidderResponseHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    BidderResponsePayload,
                    BidderInvocationContext,
                    Future<InvocationResult<BidderResponsePayload>>> delegate) {

        given(hookCatalog.rawBidderResponseHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(RawBidderResponseHookImpl.of(delegate));
    }

    private void givenProcessedBidderResponseHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    BidderResponsePayload,
                    BidderInvocationContext,
                    Future<InvocationResult<BidderResponsePayload>>> delegate) {

        given(hookCatalog.processedBidderResponseHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(ProcessedBidderResponseHookImpl.of(delegate));
    }

    private void givenAuctionResponseHook(
            String moduleCode,
            String hookImplCode,
            BiFunction<
                    AuctionResponsePayload,
                    AuctionInvocationContext,
                    Future<InvocationResult<AuctionResponsePayload>>> delegate) {

        given(hookCatalog.auctionResponseHookBy(eq(moduleCode), eq(hookImplCode)))
                .willReturn(AuctionResponseHookImpl.of(delegate));
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
        public Future<InvocationResult<EntrypointPayload>> call(EntrypointPayload payload,
                                                                InvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class RawAuctionRequestHookImpl implements RawAuctionRequestHook {

        String code = "hook-code";

        BiFunction<
                AuctionRequestPayload,
                AuctionInvocationContext,
                Future<InvocationResult<AuctionRequestPayload>>> delegate;

        @Override
        public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                    AuctionInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class ProcessedAuctionRequestHookImpl implements ProcessedAuctionRequestHook {

        String code = "hook-code";

        BiFunction<
                AuctionRequestPayload,
                AuctionInvocationContext,
                Future<InvocationResult<AuctionRequestPayload>>> delegate;

        @Override
        public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                    AuctionInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class BidderRequestHookImpl implements BidderRequestHook {

        String code = "hook-code";

        BiFunction<
                BidderRequestPayload,
                BidderInvocationContext,
                Future<InvocationResult<BidderRequestPayload>>> delegate;

        @Override
        public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload payload,
                                                                   BidderInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class RawBidderResponseHookImpl implements RawBidderResponseHook {

        String code = "hook-code";

        BiFunction<
                BidderResponsePayload,
                BidderInvocationContext,
                Future<InvocationResult<BidderResponsePayload>>> delegate;

        @Override
        public Future<InvocationResult<BidderResponsePayload>> call(BidderResponsePayload payload,
                                                                    BidderInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class ProcessedBidderResponseHookImpl implements ProcessedBidderResponseHook {

        String code = "hook-code";

        BiFunction<
                BidderResponsePayload,
                BidderInvocationContext,
                Future<InvocationResult<BidderResponsePayload>>> delegate;

        @Override
        public Future<InvocationResult<BidderResponsePayload>> call(BidderResponsePayload payload,
                                                                    BidderInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }

    @Value(staticConstructor = "of")
    @NonFinal
    private static class AuctionResponseHookImpl implements AuctionResponseHook {

        String code = "hook-code";

        BiFunction<
                AuctionResponsePayload,
                AuctionInvocationContext,
                Future<InvocationResult<AuctionResponsePayload>>> delegate;

        @Override
        public Future<InvocationResult<AuctionResponsePayload>> call(AuctionResponsePayload payload,
                                                                     AuctionInvocationContext invocationContext) {

            return delegate.apply(payload, invocationContext);
        }

        @Override
        public String code() {
            return code;
        }
    }
}
