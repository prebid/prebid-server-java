package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.time.Clock;
import java.util.ArrayList;

class StageExecutor<PAYLOAD, CONTEXT extends InvocationContext> {

    private final HookCatalog hookCatalog;
    private final Vertx vertx;
    private final Clock clock;

    private StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage;
    private String entity;
    private StageExecutionPlan executionPlan;
    private PAYLOAD initialPayload;
    private InvocationContextProvider<CONTEXT> invocationContextProvider;
    private HookExecutionContext hookExecutionContext;
    private boolean rejectAllowed;

    private StageExecutor(HookCatalog hookCatalog, Vertx vertx, Clock clock) {
        this.hookCatalog = hookCatalog;
        this.vertx = vertx;
        this.clock = clock;
    }

    public static <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> create(
            HookCatalog hookCatalog,
            Vertx vertx,
            Clock clock) {

        return new StageExecutor<>(hookCatalog, vertx, clock);
    }

    public StageExecutor<PAYLOAD, CONTEXT> withStage(StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage) {
        this.stage = stage;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withEntity(String entity) {
        this.entity = entity;
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

    public StageExecutor<PAYLOAD, CONTEXT> withInvocationContextProvider(
            InvocationContextProvider<CONTEXT> invocationContextProvider) {

        this.invocationContextProvider = invocationContextProvider;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withHookExecutionContext(HookExecutionContext hookExecutionContext) {
        this.hookExecutionContext = hookExecutionContext;
        return this;
    }

    public StageExecutor<PAYLOAD, CONTEXT> withRejectAllowed(boolean rejectAllowed) {
        this.rejectAllowed = rejectAllowed;
        return this;
    }

    public Future<HookStageExecutionResult<PAYLOAD>> execute() {
        Future<StageResult<PAYLOAD>> stageFuture = Future.succeededFuture(StageResult.of(initialPayload, entity));

        for (final ExecutionGroup group : executionPlan.getGroups()) {
            stageFuture = stageFuture.compose(stageResult ->
                    executeGroup(group, stageResult.payload())
                            .map(stageResult::applyGroupResult)
                            .compose(StageExecutor::propagateRejection));
        }

        return stageFuture
                .recover(StageExecutor::restoreResultFromRejection)
                .map(this::toHookStageExecutionResult);
    }

    private Future<GroupResult<PAYLOAD>> executeGroup(ExecutionGroup group, PAYLOAD initialPayload) {
        return GroupExecutor.<PAYLOAD, CONTEXT>create(vertx, clock)
                .withGroup(group)
                .withInitialPayload(initialPayload)
                .withHookProvider(
                        hookId -> hookCatalog.hookById(hookId.getModuleCode(), hookId.getHookImplCode(), stage))
                .withInvocationContextProvider(invocationContextProvider)
                .withHookExecutionContext(hookExecutionContext)
                .withRejectAllowed(rejectAllowed)
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

    private HookStageExecutionResult<PAYLOAD> toHookStageExecutionResult(StageResult<PAYLOAD> stageResult) {
        hookExecutionContext.getStageOutcomes().computeIfAbsent(stage.stage(), key -> new ArrayList<>())
                .add(stageResult.toStageExecutionOutcome());

        return stageResult.shouldReject()
                ? HookStageExecutionResult.reject()
                : HookStageExecutionResult.success(stageResult.payload());
    }
}
