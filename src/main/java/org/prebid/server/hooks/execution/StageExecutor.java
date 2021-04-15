package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.time.Clock;
import java.util.function.Function;

public class StageExecutor<PAYLOAD, CONTEXT extends InvocationContext> {

    private final Vertx vertx;
    private final Clock clock;

    private Stage stage;
    private StageExecutionPlan executionPlan;
    private PAYLOAD initialPayload;
    private Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider;
    private InvocationContextProvider<CONTEXT> invocationContextProvider;
    private HookExecutionContext hookExecutionContext;

    private StageExecutor(Vertx vertx, Clock clock) {
        this.vertx = vertx;
        this.clock = clock;
    }

    public static <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> create(
            Vertx vertx,
            Clock clock) {

        return new StageExecutor<>(vertx, clock);
    }

    public StageExecutor<PAYLOAD, CONTEXT> withStage(Stage stage) {
        this.stage = stage;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withExecutionPlan(StageExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withInitialPayload(PAYLOAD initialPayload) {
        this.initialPayload = initialPayload;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withHookProvider(Function<HookId, Hook<PAYLOAD, CONTEXT>> hookProvider) {
        this.hookProvider = hookProvider;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withInvocationContextProvider(
            InvocationContextProvider<CONTEXT> invocationContextProvider) {

        this.invocationContextProvider = invocationContextProvider;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withHookExecutionContext(HookExecutionContext hookExecutionContext) {
        this.hookExecutionContext = hookExecutionContext;
        return this;
    }

    public Future<HookStageExecutionResult<PAYLOAD>> execute() {
        Future<StageResult<PAYLOAD>> stageFuture = Future.succeededFuture(StageResult.of(initialPayload));

        for (final ExecutionGroup group : executionPlan.getGroups()) {
            stageFuture = stageFuture.compose(stageResult ->
                    executeGroup(group, stageResult.payload())
                            .map(stageResult::applyGroupResult)
                            .compose(StageExecutor::propagateRejection));
        }

        return stageFuture
                .recover(StageExecutor::restoreResultFromRejection)
                .map(stageResult -> toHookStageExecutionResult(stageResult, stage, hookExecutionContext));
    }

    private Future<GroupResult<PAYLOAD>> executeGroup(ExecutionGroup group, PAYLOAD initialPayload) {
        return GroupExecutor.<PAYLOAD, CONTEXT>create(vertx, clock)
                .withGroup(group)
                .withInitialPayload(initialPayload)
                .withHookProvider(hookProvider)
                .withInvocationContextProvider(invocationContextProvider)
                .execute();
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
}
