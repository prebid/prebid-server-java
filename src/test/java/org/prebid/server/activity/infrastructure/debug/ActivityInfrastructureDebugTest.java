package org.prebid.server.activity.infrastructure.debug;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.activity.infrastructure.rule.TestRule;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocation;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationDefaultResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ActivityInfrastructureDebugTest extends VertxTest {

    @Mock
    private Metrics metrics;

    @Mock
    private ActivityInvocationPayload payload;

    @Test
    public void emitActivityInvocationShouldDoNothingIfTraceLevelIsNull() {
        // given
        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitActivityInvocation(Activity.CALL_BIDDER, payload);

        // then
        assertThat(debug.trace()).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationShouldAddEntryToTheTraceWithPayloadIfTraceLevelIsBasic() {
        // given
        final ActivityInfrastructureDebug debug = debug(TraceLevel.basic);

        // when
        debug.emitActivityInvocation(Activity.CALL_BIDDER, payload);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityInvocation.of(
                "Invocation of Activity Infrastructure.",
                Activity.CALL_BIDDER,
                payload));
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationShouldAddEntryToTheTraceWithPayloadIfTraceLevelIsVerbose() {
        // given
        final ActivityInfrastructureDebug debug = debug(TraceLevel.verbose);

        // when
        debug.emitActivityInvocation(Activity.CALL_BIDDER, payload);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityInvocation.of(
                "Invocation of Activity Infrastructure.",
                Activity.CALL_BIDDER,
                payload));
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationDefaultResultShouldDoNothingIfTraceLevelIsNull() {
        // given
        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitActivityInvocationDefaultResult(true);

        // then
        assertThat(debug.trace()).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationDefaultResultShouldAddEntryToTheTraceIfTraceLevelIsBasic() {
        // given
        final ActivityInfrastructureDebug debug = debug(TraceLevel.basic);

        // when
        debug.emitActivityInvocationDefaultResult(true);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityInvocationDefaultResult.of(
                "Setting the default invocation result.",
                true));
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitProcessedRuleShouldReturnExpectedResultIfTraceLevelIsNull() {
        // given
        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitProcessedRule(TestRule.allowIfMatches(payload -> true), Rule.Result.ALLOW);

        // then
        assertThat(debug.trace()).isEmpty();
        verify(metrics).updateRequestsActivityProcessedRulesCount();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitProcessedRuleShouldReturnExpectedResultIfTraceLevelIsBasic() {
        // given
        final ActivityInfrastructureDebug debug = debug(TraceLevel.basic);

        // when
        debug.emitProcessedRule(TestRule.allowIfMatches(payload -> true), Rule.Result.ALLOW);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityRule.of(
                "Processing rule.",
                null,
                Rule.Result.ALLOW));
        verify(metrics).updateRequestsActivityProcessedRulesCount();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitProcessedRuleShouldReturnExpectedResultIfTraceLevelIsVerbose() {
        // given
        final ActivityInfrastructureDebug debug = debug(TraceLevel.verbose);

        // when
        debug.emitProcessedRule(TestRule.allowIfMatches(payload -> true), Rule.Result.ALLOW);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityRule.of(
                "Processing rule.",
                TextNode.valueOf("TestRule"),
                Rule.Result.ALLOW));
        verify(metrics).updateRequestsActivityProcessedRulesCount();
        verify(metrics).updateAccountActivityProcessedRulesCount(eq("accountId"));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationResultShouldDoNothingIfTraceLevelIsNullAndActivityAllowed() {
        // given
        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitActivityInvocationResult(Activity.CALL_BIDDER, payload, true);

        // then
        assertThat(debug.trace()).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationResultShouldReturnExpectedResultIfActivityDisallowed() {
        // given
        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitActivityInvocationResult(Activity.CALL_BIDDER, payload, false);

        // then
        assertThat(debug.trace()).isEmpty();
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationResultShouldReturnExpectedResultIfActivityDisallowedAndComponentTypeIsBidder() {
        // given
        given(payload.componentType()).willReturn(ComponentType.BIDDER);
        given(payload.componentName()).willReturn("bidder");

        final ActivityInfrastructureDebug debug = debug(null);

        // when
        debug.emitActivityInvocationResult(Activity.CALL_BIDDER, payload, false);

        // then
        assertThat(debug.trace()).isEmpty();
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verify(metrics).updateAdapterActivityDisallowedCount(eq("bidder"), eq(Activity.CALL_BIDDER));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationResultShouldReturnExpectedResultIfTraceLevelIsBasic() {
        // given
        given(payload.componentType()).willReturn(ComponentType.BIDDER);
        given(payload.componentName()).willReturn("bidder");

        final ActivityInfrastructureDebug debug = debug(TraceLevel.basic);

        // when
        debug.emitActivityInvocationResult(Activity.CALL_BIDDER, payload, false);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityInvocationResult.of(
                "Activity Infrastructure invocation result.",
                Activity.CALL_BIDDER,
                false));
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verify(metrics).updateAdapterActivityDisallowedCount(eq("bidder"), eq(Activity.CALL_BIDDER));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void emitActivityInvocationResultShouldReturnExpectedResultIfTraceLevelIsVerbose() {
        // given
        given(payload.componentType()).willReturn(ComponentType.BIDDER);
        given(payload.componentName()).willReturn("bidder");

        final ActivityInfrastructureDebug debug = debug(TraceLevel.verbose);

        // when
        debug.emitActivityInvocationResult(Activity.CALL_BIDDER, payload, false);

        // then
        assertThat(debug.trace()).containsExactly(ExtTraceActivityInvocationResult.of(
                "Activity Infrastructure invocation result.",
                Activity.CALL_BIDDER,
                false));
        verify(metrics).updateRequestsActivityDisallowedCount(eq(Activity.CALL_BIDDER));
        verify(metrics).updateAccountActivityDisallowedCount(eq("accountId"), eq(Activity.CALL_BIDDER));
        verify(metrics).updateAdapterActivityDisallowedCount(eq("bidder"), eq(Activity.CALL_BIDDER));
        verifyNoMoreInteractions(metrics);
    }

    private ActivityInfrastructureDebug debug(TraceLevel traceLevel) {
        return new ActivityInfrastructureDebug("accountId", traceLevel, metrics, jacksonMapper);
    }
}
