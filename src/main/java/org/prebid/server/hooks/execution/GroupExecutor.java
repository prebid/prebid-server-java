package org.prebid.server.hooks.execution;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.provider.HookProvider;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.time.Clock;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

class GroupExecutor<PAYLOAD, CONTEXT extends InvocationContext> {

    private final Vertx vertx;
    private final Clock clock;
    private final boolean isConfigToInvokeRequired;

    private ExecutionGroup group;
    private PAYLOAD initialPayload;
    private HookProvider<PAYLOAD, CONTEXT> hookProvider;
    private InvocationContextProvider<CONTEXT> invocationContextProvider;
    private HookExecutionContext hookExecutionContext;
    private boolean rejectAllowed;

    private GroupExecutor(Vertx vertx, Clock clock, boolean isConfigToInvokeRequired) {
        this.vertx = vertx;
        this.clock = clock;
        this.isConfigToInvokeRequired = isConfigToInvokeRequired;
    }

    public static <PAYLOAD, CONTEXT extends InvocationContext> GroupExecutor<PAYLOAD, CONTEXT> create(
            Vertx vertx,
            Clock clock,
            boolean isConfigToInvokeRequired) {

        return new GroupExecutor<>(vertx, clock, isConfigToInvokeRequired);
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withGroup(ExecutionGroup group) {
        this.group = group;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withInitialPayload(PAYLOAD initialPayload) {
        this.initialPayload = initialPayload;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withHookProvider(HookProvider<PAYLOAD, CONTEXT> hookProvider) {
        this.hookProvider = hookProvider;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withInvocationContextProvider(
            InvocationContextProvider<CONTEXT> invocationContextProvider) {

        this.invocationContextProvider = invocationContextProvider;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withHookExecutionContext(HookExecutionContext hookExecutionContext) {
        this.hookExecutionContext = hookExecutionContext;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withRejectAllowed(boolean rejectAllowed) {
        this.rejectAllowed = rejectAllowed;
        return this;
    }

    public Future<GroupResult<PAYLOAD>> execute() {
        final GroupResult<PAYLOAD> initialGroupResult = GroupResult.of(initialPayload, rejectAllowed);
        Future<GroupResult<PAYLOAD>> groupFuture = Future.succeededFuture(initialGroupResult);

        for (final HookId hookId : group.getHookSequence()) {
            final long startTime = clock.millis();
            final Future<InvocationResult<PAYLOAD>> invocationResult =
                    executeHook(hookId, group.getTimeout(), initialGroupResult);

            groupFuture = groupFuture.compose(groupResult ->
                    applyInvocationResult(invocationResult, hookId, startTime, groupResult));
        }

        return groupFuture.recover(GroupExecutor::restoreResultFromRejection);
    }

    private Future<InvocationResult<PAYLOAD>> executeHook(HookId hookId,
                                                          Long timeout,
                                                          GroupResult<PAYLOAD> groupResult) {

        final Hook<PAYLOAD, CONTEXT> hook;
        try {
            hook = hookProvider.apply(hookId);
        } catch (Exception e) {
            return Future.failedFuture(new FailedException(e.getMessage()));
        }

        final CONTEXT invocationContext = invocationContextProvider.apply(timeout, hookId, moduleContextFor(hookId));

        if (isConfigToInvokeRequired && invocationContext instanceof AuctionInvocationContext) {
            final ObjectNode accountConfig = ((AuctionInvocationContext) invocationContext).accountConfig();
            if (accountConfig == null || accountConfig.isNull()) {
                return Future.succeededFuture(InvocationResultImpl.<PAYLOAD>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_invocation)
                        .build());
            }
        }

        return executeWithTimeout(() -> hook.call(groupResult.payload(), invocationContext), timeout);
    }

    private <T> Future<T> executeWithTimeout(Supplier<Future<T>> action, Long timeout) {
        final Promise<T> promise = Promise.promise();

        final long timeoutTimerId = vertx.setTimer(timeout, id -> failWithTimeout(promise));

        executeSafely(action)
                .onComplete(result -> completeWithActionResult(promise, timeoutTimerId, result));

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

    private Future<GroupResult<PAYLOAD>> applyInvocationResult(
            Future<InvocationResult<PAYLOAD>> invocationResult,
            HookId hookId,
            long startTime,
            GroupResult<PAYLOAD> groupResult) {

        return invocationResult
                .map(result -> {
                    saveModuleContext(hookId, result);
                    return groupResult.applyInvocationResult(result, hookId, executionTime(startTime));
                })
                .otherwise(throwable -> groupResult.applyFailure(throwable, hookId, executionTime(startTime)))
                .compose(this::propagateRejection);
    }

    private Object moduleContextFor(HookId hookId) {
        return hookExecutionContext.getModuleContexts().get(hookId.getModuleCode());
    }

    private void saveModuleContext(HookId hookId, InvocationResult<PAYLOAD> result) {
        hookExecutionContext.getModuleContexts().put(hookId.getModuleCode(), result.moduleContext());
    }

    private Future<GroupResult<PAYLOAD>> propagateRejection(GroupResult<PAYLOAD> groupResult) {
        return groupResult.shouldReject()
                ? Future.failedFuture(new RejectedException(groupResult))
                : Future.succeededFuture(groupResult);

    }

    private static <T> Future<T> restoreResultFromRejection(Throwable throwable) {
        if (throwable instanceof RejectedException) {
            return Future.succeededFuture(((RejectedException) throwable).result());
        }

        return Future.failedFuture(throwable);
    }
}
