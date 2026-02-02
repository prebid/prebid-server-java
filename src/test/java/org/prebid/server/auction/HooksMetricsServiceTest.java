package org.prebid.server.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class HooksMetricsServiceTest extends VertxTest {

    @Mock
    private Metrics metrics;

    private HooksMetricsService target;

    @BeforeEach
    public void before() {
        target = new HooksMetricsService(metrics);
    }

    @Test
    public void shouldIncrementHooksGlobalMetrics() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(HookExecutionContext.of(
                        HookHttpEndpoint.POST_AUCTION,
                        stageOutcomes(givenAppliedToImpl())))
                .debugContext(DebugContext.empty())
                .requestRejected(true)
                .build();

        // when
        target.updateHooksMetrics(auctionContext);

        // then
        verify(metrics, times(6)).updateHooksMetrics(anyString(), any(), any(), any(), any(), any());
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook1"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.update));
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook2"),
                eq(ExecutionStatus.invocation_failure),
                eq(6L),
                isNull());
        verify(metrics).updateHooksMetrics(
                eq("module1"),
                eq(Stage.entrypoint),
                eq("hook2"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.no_action));
        verify(metrics).updateHooksMetrics(
                eq("module2"),
                eq(Stage.entrypoint),
                eq("hook1"),
                eq(ExecutionStatus.timeout),
                eq(6L),
                isNull());
        verify(metrics).updateHooksMetrics(
                eq("module3"),
                eq(Stage.auction_response),
                eq("hook1"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.update));
        verify(metrics).updateHooksMetrics(
                eq("module3"),
                eq(Stage.auction_response),
                eq("hook2"),
                eq(ExecutionStatus.success),
                eq(4L),
                eq(ExecutionAction.no_action));
        verify(metrics, never()).updateAccountHooksMetrics(any(), any(), any(), any());
        verify(metrics, never()).updateAccountModuleDurationMetric(any(), any(), any());
    }

    @Test
    public void shouldIncrementHooksGlobalAndAccountMetrics() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(HookExecutionContext.of(
                        HookHttpEndpoint.POST_AUCTION,
                        stageOutcomes(givenAppliedToImpl())))
                .debugContext(DebugContext.empty())
                .requestRejected(true)
                .account(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build())
                .build();

        // when
        target.updateHooksMetrics(auctionContext);

        // then
        verify(metrics, times(6)).updateHooksMetrics(anyString(), any(), any(), any(), any(), any());
        verify(metrics, times(6)).updateAccountHooksMetrics(any(), any(), any(), any());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.update));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.invocation_failure),
                isNull());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module1"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.no_action));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module2"),
                eq(ExecutionStatus.timeout),
                isNull());
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module3"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.update));
        verify(metrics).updateAccountHooksMetrics(
                any(),
                eq("module3"),
                eq(ExecutionStatus.success),
                eq(ExecutionAction.no_action));
        verify(metrics, times(3)).updateAccountModuleDurationMetric(any(), any(), any());
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module1"), eq(14L));
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module2"), eq(6L));
        verify(metrics).updateAccountModuleDurationMetric(any(), eq("module3"), eq(8L));
    }

    private static AppliedToImpl givenAppliedToImpl() {
        return AppliedToImpl.builder()
                .impIds(asList("impId1", "impId2"))
                .request(true)
                .build();
    }

    private static EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes(AppliedToImpl appliedToImp) {
        final Map<Stage, List<StageExecutionOutcome>> stageOutcomes = new HashMap<>();

        stageOutcomes.put(Stage.entrypoint, singletonList(StageExecutionOutcome.of(
                "http-request",
                asList(
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook1"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 1-1")
                                        .action(ExecutionAction.update)
                                        .errors(asList("error message 1-1 1", "error message 1-1 2"))
                                        .warnings(asList("warning message 1-1 1", "warning message 1-1 2"))
                                        .debugMessages(asList("debug message 1-1 1", "debug message 1-1 2"))
                                        .analyticsTags(TagsImpl.of(singletonList(
                                                ActivityImpl.of(
                                                        "some-activity",
                                                        "success",
                                                        singletonList(ResultImpl.of(
                                                                "success",
                                                                mapper.createObjectNode(),
                                                                appliedToImp))))))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook2"))
                                        .executionTime(6L)
                                        .status(ExecutionStatus.invocation_failure)
                                        .message("Message 1-2")
                                        .errors(asList("error message 1-2 1", "error message 1-2 2"))
                                        .warnings(asList("warning message 1-2 1", "warning message 1-2 2"))
                                        .build())),
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module1", "hook2"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 1-2")
                                        .action(ExecutionAction.no_action)
                                        .errors(asList("error message 1-2 3", "error message 1-2 4"))
                                        .warnings(asList("warning message 1-2 3", "warning message 1-2 4"))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module2", "hook1"))
                                        .executionTime(6L)
                                        .status(ExecutionStatus.timeout)
                                        .message("Message 2-1")
                                        .errors(asList("error message 2-1 1", "error message 2-1 2"))
                                        .warnings(asList("warning message 2-1 1", "warning message 2-1 2"))
                                        .build()))))));

        stageOutcomes.put(Stage.auction_response, singletonList(StageExecutionOutcome.of(
                "auction-response",
                singletonList(
                        GroupExecutionOutcome.of(asList(
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module3", "hook1"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .message("Message 3-1")
                                        .action(ExecutionAction.update)
                                        .errors(asList("error message 3-1 1", "error message 3-1 2"))
                                        .warnings(asList("warning message 3-1 1", "warning message 3-1 2"))
                                        .build(),
                                HookExecutionOutcome.builder()
                                        .hookId(HookId.of("module3", "hook2"))
                                        .executionTime(4L)
                                        .status(ExecutionStatus.success)
                                        .action(ExecutionAction.no_action)
                                        .build()))))));

        return new EnumMap<>(stageOutcomes);
    }

}
