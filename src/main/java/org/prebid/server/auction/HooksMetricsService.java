package org.prebid.server.auction;

import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HooksMetricsService {

    private final Metrics metrics;

    public HooksMetricsService(Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    public AuctionContext updateHooksMetrics(AuctionContext context) {
        final EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes =
                context.getHookExecutionContext().getStageOutcomes();

        final Account account = context.getAccount();

        stageOutcomes.forEach((stage, outcomes) -> updateHooksStageMetrics(account, stage, outcomes));

        // account might be null if request is rejected by the entrypoint hook
        if (account != null) {
            stageOutcomes.values().stream()
                    .flatMap(Collection::stream)
                    .map(StageExecutionOutcome::getGroups)
                    .flatMap(Collection::stream)
                    .map(GroupExecutionOutcome::getHooks)
                    .flatMap(Collection::stream)
                    .filter(hookOutcome -> hookOutcome.getAction() != ExecutionAction.no_invocation)
                    .collect(Collectors.groupingBy(
                            outcome -> outcome.getHookId().getModuleCode(),
                            Collectors.summingLong(HookExecutionOutcome::getExecutionTime)))
                    .forEach((moduleCode, executionTime) ->
                            metrics.updateAccountModuleDurationMetric(account, moduleCode, executionTime));
        }

        return context;
    }

    private void updateHooksStageMetrics(Account account, Stage stage, List<StageExecutionOutcome> stageOutcomes) {
        stageOutcomes.stream()
                .flatMap(stageOutcome -> stageOutcome.getGroups().stream())
                .flatMap(groupOutcome -> groupOutcome.getHooks().stream())
                .forEach(hookOutcome -> updateHookInvocationMetrics(account, stage, hookOutcome));
    }

    private void updateHookInvocationMetrics(Account account, Stage stage, HookExecutionOutcome hookOutcome) {
        final HookId hookId = hookOutcome.getHookId();
        final ExecutionStatus status = hookOutcome.getStatus();
        final ExecutionAction action = hookOutcome.getAction();
        final String moduleCode = hookId.getModuleCode();

        metrics.updateHooksMetrics(
                moduleCode,
                stage,
                hookId.getHookImplCode(),
                status,
                hookOutcome.getExecutionTime(),
                action);

        // account might be null if request is rejected by the entrypoint hook
        if (account != null) {
            metrics.updateAccountHooksMetrics(account, moduleCode, status, action);
        }
    }
}
