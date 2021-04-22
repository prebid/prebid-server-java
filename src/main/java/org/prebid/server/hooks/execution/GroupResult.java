package org.prebid.server.hooks.execution;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Accessors(fluent = true)
@Getter
class GroupResult<T> {

    private boolean shouldReject;

    private T payload;

    private final boolean rejectAllowed;

    private final List<HookExecutionOutcome> hookExecutionOutcomes = new ArrayList<>();

    private GroupResult(T payload, boolean rejectAllowed) {
        this.shouldReject = false;
        this.payload = payload;
        this.rejectAllowed = rejectAllowed;
    }

    public static <T> GroupResult<T> of(T payload, boolean rejectAllowed) {
        return new GroupResult<>(payload, rejectAllowed);
    }

    public GroupResult<T> applyInvocationResult(InvocationResult<T> invocationResult,
                                                HookId hookId,
                                                long executionTime) {

        hookExecutionOutcomes.add(toExecutionOutcome(invocationResult, hookId, executionTime));

        if (invocationResult.status() == InvocationStatus.success && invocationResult.action() != null) {
            switch (invocationResult.action()) {
                case reject:
                    applyReject();
                    break;
                case update:
                    applyPayloadUpdate(invocationResult.payloadUpdate());
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

    private void applyReject() {
        if (!rejectAllowed) {
            // TODO: log error?
            return;
        }

        shouldReject = true;
        payload = null;
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

    private void applyPayloadUpdate(PayloadUpdate<T> payloadUpdate) {
        if (payloadUpdate == null) {
            // TODO: log error?
            return;
        }

        try {
            payload = payloadUpdate.apply(payload);
        } catch (Exception e) {
            // TODO: log error?
        }
    }
}
