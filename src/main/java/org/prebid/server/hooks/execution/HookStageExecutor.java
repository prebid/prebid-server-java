package org.prebid.server.hooks.execution;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
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
import org.prebid.server.hooks.execution.model.InvocationContextImpl;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.HookCatalog;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class HookStageExecutor {

    private final ExecutionPlan executionPlan;
    private final HookCatalog hookCatalog;
    private final TimeoutFactory timeoutFactory;
    private final Vertx vertx;
    private final Clock clock;

    private HookStageExecutor(ExecutionPlan executionPlan,
                              HookCatalog hookCatalog,
                              TimeoutFactory timeoutFactory,
                              Vertx vertx,
                              Clock clock) {

        this.executionPlan = executionPlan;
        this.hookCatalog = hookCatalog;
        this.timeoutFactory = timeoutFactory;
        this.vertx = vertx;
        this.clock = clock;
    }

    public static HookStageExecutor create(String executionPlan,
                                           HookCatalog hookCatalog,
                                           TimeoutFactory timeoutFactory,
                                           Vertx vertx,
                                           Clock clock,
                                           JacksonMapper mapper) {

        return new HookStageExecutor(
                parseExecutionPlan(executionPlan, Objects.requireNonNull(mapper)),
                Objects.requireNonNull(hookCatalog),
                Objects.requireNonNull(timeoutFactory),
                Objects.requireNonNull(vertx),
                Objects.requireNonNull(clock));
    }

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            HookExecutionContext context) {

        final Endpoint endpoint = context.getEndpoint();
        final StageExecutionPlan executionPlan = planForEntrypointStage(endpoint);

        return executeStage(
                executionPlan,
                EntrypointPayloadImpl.of(queryParams, headers, body),
                hookId -> hookCatalog.entrypointHookBy(hookId.getModuleCode(), hookId.getHookImplCode()),
                timeout -> InvocationContextImpl.of(timeoutFactory.create(timeout), endpoint),
                Stage.entrypoint,
                context);
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeRawAuctionRequestStage(
            BidRequest bidRequest,
            Account account,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, () -> bidRequest));
    }

    public Future<HookStageExecutionResult<BidderRequestPayload>> executeBidderRequestStage(
            BidderRequest bidderRequest,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new BidderRequestPayload() {
            @Override
            public BidRequest bidRequest() {
                return bidderRequest.getBidRequest();
            }
        }));
    }

    public Future<HookStageExecutionResult<AuctionResponsePayload>> executeAuctionResponseStage(
            BidResponse bidResponse,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new AuctionResponsePayload() {
            @Override
            public BidResponse bidResponse() {
                return bidResponse;
            }
        }));
    }

    private static ExecutionPlan parseExecutionPlan(String executionPlan, JacksonMapper mapper) {
        if (StringUtils.isBlank(executionPlan)) {
            return ExecutionPlan.empty();
        }

        try {
            return mapper.decodeValue(executionPlan, ExecutionPlan.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Hooks execution plan could not be parsed", e);
        }
    }

    private StageExecutionPlan planForEntrypointStage(Endpoint endpoint) {
        return planFor(executionPlan, endpoint, Stage.entrypoint);
    }

    private StageExecutionPlan planFor(Account account, Endpoint endpoint, Stage stage) {
        return planFor(effectiveExecutionPlanFor(account), endpoint, stage);
    }

    private static StageExecutionPlan planFor(ExecutionPlan executionPlan, Endpoint endpoint, Stage stage) {
        return executionPlan
                .getEndpoints()
                .getOrDefault(endpoint, EndpointExecutionPlan.empty())
                .getStages()
                .getOrDefault(stage, StageExecutionPlan.empty());
    }

    private ExecutionPlan effectiveExecutionPlanFor(Account account) {
        final AccountHooksConfiguration hooksAccountConfig = account.getHooks();
        final ExecutionPlan accountExecutionPlan =
                hooksAccountConfig != null ? hooksAccountConfig.getExecutionPlan() : null;

        return accountExecutionPlan != null ? accountExecutionPlan : executionPlan;
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> Future<HookStageExecutionResult<PAYLOAD>> executeStage(
            StageExecutionPlan executionPlan,
            PAYLOAD initialPayload,
            Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider,
            Function<Long, CONTEXT> contextProvider,
            Stage stage,
            HookExecutionContext hookExecutionContext) {

        Future<StageResult<PAYLOAD>> stageFuture = Future.succeededFuture(StageResult.of(initialPayload));

        for (final ExecutionGroup group : executionPlan.getGroups()) {
            stageFuture = stageFuture.compose(stageResult ->
                    executeGroup(group, stageResult.payload(), hookProvider, contextProvider)
                            .map(stageResult::applyGroupResult)
                            .compose(HookStageExecutor::propagateRejection));
        }

        return stageFuture
                .recover(HookStageExecutor::restoreResultFromRejection)
                .map(stageResult -> toHookStageExecutionResult(stageResult, stage, hookExecutionContext));
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> Future<GroupResult<PAYLOAD>> executeGroup(
            ExecutionGroup group,
            PAYLOAD initialPayload,
            Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider,
            Function<Long, CONTEXT> contextProvider) {

        final GroupResult<PAYLOAD> initialGroupResult = GroupResult.of(initialPayload);
        Future<GroupResult<PAYLOAD>> groupFuture = Future.succeededFuture(initialGroupResult);

        for (final HookId hookId : group.getHookSequence()) {
            final Hook<PAYLOAD, CONTEXT> hook = hookProvider.apply(hookId);

            groupFuture = executeHookAndChainResult(
                    groupFuture, hook, hookId, initialGroupResult, contextProvider, group);
        }

        return groupFuture.recover(HookStageExecutor::restoreResultFromRejection);
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> Future<GroupResult<PAYLOAD>> executeHookAndChainResult(
            Future<GroupResult<PAYLOAD>> groupFuture,
            Hook<PAYLOAD, CONTEXT> hook,
            HookId hookId,
            GroupResult<PAYLOAD> initialGroupResult,
            Function<Long, CONTEXT> contextProvider,
            ExecutionGroup group) {

        if (!group.getSynchronous()) {
            final long startTime = clock.millis();
            final Future<InvocationResult<PAYLOAD>> invocationResult =
                    executeHook(hook, group.getTimeout(), initialGroupResult, contextProvider);

            return groupFuture.compose(groupResult ->
                    applyInvocationResult(
                            invocationResult,
                            hookId,
                            startTime,
                            groupResult));
        }

        return groupFuture.compose(groupResult -> {
            final long startTime = clock.millis();
            return applyInvocationResult(
                    executeHook(hook, hookTimeoutFromSyncGroup(group), groupResult, contextProvider),
                    hookId,
                    startTime,
                    groupResult);
        });
    }

    private static long hookTimeoutFromSyncGroup(ExecutionGroup group) {
        return group.getTimeout() / group.getHookSequence().size();
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> Future<InvocationResult<PAYLOAD>> executeHook(
            Hook<PAYLOAD, CONTEXT> hook,
            Long timeout,
            GroupResult<PAYLOAD> groupResult,
            Function<Long, CONTEXT> contextProvider) {

        if (hook == null) {
            // TODO: log error?
            return Future.failedFuture(new FailedException("Hook implementation does not exist or disabled"));
        }

        return executeWithTimeout(
                () -> hook.call(groupResult.payload(), contextProvider.apply(timeout)),
                timeout);
    }

    private <T> Future<T> executeWithTimeout(Supplier<Future<T>> action, Long timeout) {
        final Promise<T> promise = Promise.promise();

        final long timeoutTimerId = vertx.setTimer(timeout, id -> failWithTimeout(promise));

        executeSafely(action)
                .setHandler(result -> completeWithActionResult(promise, timeoutTimerId, result));

        return promise.future();
    }

    private static <T> void failWithTimeout(Promise<T> promise) {
        // no need for synchronization since timer is fired on the same event loop thread
        if (!promise.future().isComplete()) {
            promise.fail(new TimeoutException("Timed out while executing action"));
        }
    }

    private static <T> Future<T> executeSafely(Supplier<Future<T>> action) {
        try {
            final Future<T> result = action.get();
            return result != null ? result : Future.failedFuture(new FailedException("Action returned null"));
        } catch (Throwable e) {
            return Future.failedFuture(new FailedException(e));
        }
    }

    private <T> void completeWithActionResult(Promise<T> promise, long timeoutTimerId, AsyncResult<T> result) {
        vertx.cancelTimer(timeoutTimerId);

        // check is to avoid harmless exception if timeout exceeds before successful result becomes ready
        if (!promise.future().isComplete()) {
            promise.handle(result);
        }
    }

    private long executionTime(long startTime) {
        return clock.millis() - startTime;
    }

    private <PAYLOAD> Future<GroupResult<PAYLOAD>> applyInvocationResult(
            Future<InvocationResult<PAYLOAD>> invocationResult,
            HookId hookId,
            long startTime,
            GroupResult<PAYLOAD> groupResult) {

        return invocationResult
                .map(result -> groupResult.applyInvocationResult(result, hookId, executionTime(startTime)))
                .otherwise(throwable -> groupResult.applyFailure(throwable, hookId, executionTime(startTime)))
                .compose(HookStageExecutor::propagateRejection);
    }

    private static <PAYLOAD> Future<GroupResult<PAYLOAD>> propagateRejection(GroupResult<PAYLOAD> groupResult) {
        return groupResult.shouldReject()
                ? Future.failedFuture(new RejectedException(groupResult))
                : Future.succeededFuture(groupResult);

    }

    private static <PAYLOAD> Future<StageResult<PAYLOAD>> propagateRejection(StageResult<PAYLOAD> stageResult) {
        return stageResult.shouldReject()
                ? Future.failedFuture(new RejectedException(stageResult))
                : Future.succeededFuture(stageResult);

    }

    private static <T> Future<T> restoreResultFromRejection(Throwable throwable) {
        if (throwable instanceof RejectedException) {
            return Future.succeededFuture(((RejectedException) throwable).result());
        }

        return Future.failedFuture(throwable);
    }

    private static <PAYLOAD> HookStageExecutionResult<PAYLOAD> toHookStageExecutionResult(
            StageResult<PAYLOAD> stageResult,
            Stage stage,
            HookExecutionContext hookExecutionContext) {

        hookExecutionContext.getStageOutcomes().put(stage, stageResult.toStageExecutionOutcome());

        return stageResult.shouldReject()
                ? HookStageExecutionResult.reject()
                : HookStageExecutionResult.success(stageResult.payload());
    }

    @Accessors(fluent = true)
    @Getter
    private static class GroupResult<T> {

        private boolean shouldReject;

        private T payload;

        private final List<HookExecutionOutcome> hookExecutionOutcomes = new ArrayList<>();

        private GroupResult(T payload) {
            this.shouldReject = false;
            this.payload = payload;
        }

        public static <T> GroupResult<T> of(T payload) {
            return new GroupResult<>(payload);
        }

        public GroupResult<T> applyInvocationResult(InvocationResult<T> invocationResult,
                                                    HookId hookId,
                                                    long executionTime) {

            hookExecutionOutcomes.add(toExecutionOutcome(invocationResult, hookId, executionTime));

            if (invocationResult.status() == InvocationStatus.success && invocationResult.action() != null) {
                switch (invocationResult.action()) {
                    case reject:
                        shouldReject = true;
                        payload = null;
                        break;
                    case update:
                        payload = applyPayloadUpdate(invocationResult.payloadUpdate());
                        break;
                    case no_action:
                        break;
                    default:
                        throw new IllegalStateException(
                                String.format("Unknown invocation action %s", invocationResult.action()));
                }
            }

            return this;
        }

        public GroupResult<T> applyFailure(Throwable throwable, HookId hookId, long executionTime) {
            hookExecutionOutcomes.add(toExecutionOutcome(throwable, hookId, executionTime));

            return this;
        }

        public GroupExecutionOutcome toGroupExecutionOutcome() {
            return GroupExecutionOutcome.of(this.hookExecutionOutcomes());
        }

        private static HookExecutionOutcome toExecutionOutcome(InvocationResult<?> invocationResult,
                                                               HookId hookId,
                                                               long executionTime) {

            return HookExecutionOutcome.builder()
                    .hookId(hookId)
                    .executionTime(executionTime)
                    .status(toExecutionStatus(invocationResult.status()))
                    .message(invocationResult.message())
                    .action(toExecutionAction(invocationResult.action()))
                    .errors(invocationResult.errors())
                    .warnings(invocationResult.warnings())
                    .debugMessages(invocationResult.debugMessages())
                    .build();
        }

        private static HookExecutionOutcome toExecutionOutcome(Throwable throwable, HookId hookId, long executionTime) {
            return HookExecutionOutcome.builder()
                    .hookId(hookId)
                    .executionTime(executionTime)
                    .status(toFailureType(throwable))
                    .message(throwable.getMessage())
                    .build();
        }

        private static ExecutionStatus toFailureType(Throwable throwable) {
            if (throwable instanceof TimeoutException) {
                return ExecutionStatus.timeout;
            } else if (throwable instanceof FailedException) {
                return ExecutionStatus.invocation_failure;
            }

            return ExecutionStatus.execution_failure;
        }

        private static ExecutionStatus toExecutionStatus(InvocationStatus status) {
            if (status == null) {
                return null;
            }

            switch (status) {
                case success:
                    return ExecutionStatus.success;
                case failure:
                    return ExecutionStatus.failure;
                default:
                    throw new IllegalStateException(String.format("Unknown invocation status %s", status));
            }
        }

        private static ExecutionAction toExecutionAction(InvocationAction action) {
            if (action == null) {
                return null;
            }

            switch (action) {
                case reject:
                    return ExecutionAction.reject;
                case update:
                    return ExecutionAction.update;
                case no_action:
                    return ExecutionAction.no_action;
                default:
                    throw new IllegalStateException(String.format("Unknown invocation action %s", action));
            }
        }

        private T applyPayloadUpdate(PayloadUpdate<T> payloadUpdate) {
            if (payloadUpdate == null) {
                // TODO: log error?
                return payload;
            }

            try {
                return payloadUpdate.apply(payload);
            } catch (Exception e) {
                // TODO: log error?
                return payload;
            }
        }
    }

    @Accessors(fluent = true)
    @Getter
    private static class StageResult<T> {

        private boolean shouldReject;

        private T payload;

        private final List<GroupResult<T>> groupResults = new ArrayList<>();

        private StageResult(T payload) {
            this.shouldReject = false;
            this.payload = payload;
        }

        public static <T> StageResult<T> of(T payload) {
            return new StageResult<>(payload);
        }

        public StageResult<T> applyGroupResult(GroupResult<T> groupResult) {
            groupResults.add(groupResult);

            shouldReject = groupResult.shouldReject();
            payload = groupResult.payload();

            return this;
        }

        public StageExecutionOutcome toStageExecutionOutcome() {
            return StageExecutionOutcome.of(this.groupResults().stream()
                    .map(GroupResult::toGroupExecutionOutcome)
                    .collect(Collectors.toList()));
        }
    }

    private static class RejectedException extends RuntimeException {

        private final Object result;

        RejectedException(Object result) {
            this.result = result;
        }

        @SuppressWarnings("unchecked")
        public <T> T result() {
            return (T) result;
        }
    }

    private static class FailedException extends RuntimeException {

        FailedException(String message) {
            super(message);
        }

        FailedException(Throwable cause) {
            super(cause);
        }
    }
}
