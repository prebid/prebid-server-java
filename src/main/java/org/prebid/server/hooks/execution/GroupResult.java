package org.prebid.server.hooks.execution;

import io.vertx.core.logging.LoggerFactory;
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
import org.prebid.server.log.ConditionalLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Accessors(fluent = true)
@Getter
class GroupResult<T> {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(GroupExecutor.class));

    private static final double LOG_SAMPLING_RATE = 0.01d;

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

        if (invocationResult.status() == InvocationStatus.success && invocationResult.action() != null) {
            try {
                applyAction(hookId, invocationResult.action(), invocationResult.payloadUpdate());
            } catch (Exception e) {
                hookExecutionOutcomes.add(toExecutionOutcome(e, hookId, executionTime));

                return this;
            }
        }

        hookExecutionOutcomes.add(toExecutionOutcome(invocationResult, hookId, executionTime));

        return this;
    }

    public GroupResult<T> applyFailure(Throwable throwable, HookId hookId, long executionTime) {
        hookExecutionOutcomes.add(toExecutionOutcome(throwable, hookId, executionTime));

        return this;
    }

    public GroupExecutionOutcome toGroupExecutionOutcome() {
        return GroupExecutionOutcome.of(this.hookExecutionOutcomes());
    }

    private void applyAction(HookId hookId, InvocationAction action, PayloadUpdate<T> payloadUpdate) {
        switch (action) {
            case reject:
                applyReject(hookId);
                break;
            case update:
                applyPayloadUpdate(hookId, payloadUpdate);
                break;
            case no_action:
                break;
            default:
                throw new IllegalStateException(
                        String.format("Unknown invocation action %s", action));
        }
    }

    private void applyReject(HookId hookId) {
        if (!rejectAllowed) {
            conditionalLogger.error(
                    String.format(
                            "Hook implementation %s requested to reject an entity on a stage that does not support "
                                    + "rejection",
                            hookId),
                    LOG_SAMPLING_RATE);

            throw new RejectionNotSupportedException("Rejection is not supported during this stage");
        }

        shouldReject = true;
        payload = null;
    }

    private void applyPayloadUpdate(HookId hookId, PayloadUpdate<T> payloadUpdate) {
        if (payloadUpdate == null) {
            conditionalLogger.error(
                    String.format(
                            "Hook implementation %s requested to update an entity but not provided a payload update",
                            hookId),
                    LOG_SAMPLING_RATE);

            throw new PayloadUpdateException("Payload update is missing in invocation result");
        }

        try {
            payload = payloadUpdate.apply(payload);
        } catch (Exception e) {
            conditionalLogger.error(
                    String.format(
                            "Hook implementation %s requested to update an entity but payload update has thrown an "
                                    + "exception: %s",
                            hookId,
                            e),
                    LOG_SAMPLING_RATE);

            throw new PayloadUpdateException(String.format("Payload update has thrown an exception: %s", e));
        }
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
                .analyticsTags(invocationResult.analyticsTags())
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

    private static class RejectionNotSupportedException extends RuntimeException {

        RejectionNotSupportedException(String message) {
            super(message);
        }
    }

    private static class PayloadUpdateException extends RuntimeException {

        PayloadUpdateException(String message) {
            super(message);
        }
    }
}
