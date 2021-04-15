package org.prebid.server.hooks.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.InvocationContextProvider;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;

import java.time.Clock;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public class GroupExecutor<PAYLOAD, CONTEXT extends InvocationContext> {

    private final Vertx vertx;
    private final Clock clock;

    private ExecutionGroup group;
    private PAYLOAD initialPayload;
    private Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider;
    private InvocationContextProvider<CONTEXT> invocationContextProvider;

    private GroupExecutor(Vertx vertx, Clock clock) {
        this.vertx = vertx;
        this.clock = clock;
    }

    public static <PAYLOAD, CONTEXT extends InvocationContext> GroupExecutor<PAYLOAD, CONTEXT> create(
            Vertx vertx,
            Clock clock) {

        return new GroupExecutor<>(vertx, clock);
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withGroup(ExecutionGroup group) {
        this.group = group;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withInitialPayload(PAYLOAD initialPayload) {
        this.initialPayload = initialPayload;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withHookProvider(Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider) {
        this.hookProvider = hookProvider;
        return this;
    }

    public GroupExecutor<PAYLOAD, CONTEXT> withInvocationContextProvider(
            InvocationContextProvider<CONTEXT> invocationContextProvider) {

        this.invocationContextProvider = invocationContextProvider;
        return this;
    }

    public Future<GroupResult<PAYLOAD>> execute() {
        final GroupResult<PAYLOAD> initialGroupResult = GroupResult.of(initialPayload);
        Future<GroupResult<PAYLOAD>> groupFuture = Future.succeededFuture(initialGroupResult);

        for (final HookId hookId : group.getHookSequence()) {
            final Hook<PAYLOAD, CONTEXT> hook = hookProvider.apply(hookId);

            groupFuture = executeHookAndChainResult(groupFuture, hook, hookId, initialGroupResult);
        }

        return groupFuture.recover(GroupExecutor::restoreResultFromRejection);
    }

    private Future<GroupResult<PAYLOAD>> executeHookAndChainResult(
            Future<GroupResult<PAYLOAD>> groupFuture,
            Hook<PAYLOAD, CONTEXT> hook,
            HookId hookId,
            GroupResult<PAYLOAD> initialGroupResult) {

        if (!group.getSynchronous()) {
            final long startTime = clock.millis();
            final Future<InvocationResult<PAYLOAD>> invocationResult =
                    executeHook(hook, group.getTimeout(), initialGroupResult, hookId);

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
                    executeHook(hook, hookTimeoutFromSyncGroup(group), groupResult, hookId),
                    hookId,
                    startTime,
                    groupResult);
        });
    }

    private static long hookTimeoutFromSyncGroup(ExecutionGroup group) {
        return group.getTimeout() / group.getHookSequence().size();
    }

    private Future<InvocationResult<PAYLOAD>> executeHook(
            Hook<PAYLOAD, CONTEXT> hook,
            Long timeout,
            GroupResult<PAYLOAD> groupResult,
            HookId hookId) {

        if (hook == null) {
            // TODO: log error?
            return Future.failedFuture(new FailedException("Hook implementation does not exist or disabled"));
        }

        return executeWithTimeout(
                () -> hook.call(groupResult.payload(), invocationContextProvider.apply(timeout, hookId)),
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

    private Future<GroupResult<PAYLOAD>> applyInvocationResult(
            Future<InvocationResult<PAYLOAD>> invocationResult,
            HookId hookId,
            long startTime,
            GroupResult<PAYLOAD> groupResult) {

        return invocationResult
                .map(result -> groupResult.applyInvocationResult(result, hookId, executionTime(startTime)))
                .otherwise(throwable -> groupResult.applyFailure(throwable, hookId, executionTime(startTime)))
                .compose(this::propagateRejection);
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
