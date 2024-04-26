package org.prebid.server.activity.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.debug.ActivityInfrastructureDebug;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInfrastructure;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ActivityInfrastructureTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ActivityController activityController;

    @Mock
    private ActivityInfrastructureDebug debug;

    private ActivityInfrastructure infrastructure;

    @BeforeEach
    public void setUp() {
        infrastructure = new ActivityInfrastructure(
                Arrays.stream(Activity.values())
                        .collect(Collectors.toMap(
                                UnaryOperator.identity(),
                                key -> activityController)),
                debug);
    }

    @Test
    public void creationShouldFailOnInvalidConfiguration() {
        // when and then
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> new ActivityInfrastructure(Map.of(Activity.CALL_BIDDER, activityController), debug));
    }

    @Test
    public void isAllowedShouldReturnFalse() {
        // given
        given(activityController.isAllowed(argThat(arg -> arg.componentType().equals(ComponentType.BIDDER))))
                .willReturn(false);

        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder");

        // when
        final boolean result = infrastructure.isAllowed(Activity.CALL_BIDDER, payload);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isAllowedShouldReturnTrue() {
        // given
        given(activityController.isAllowed(argThat(arg -> arg.componentType().equals(ComponentType.BIDDER))))
                .willReturn(true);

        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder");

        // when
        final boolean result = infrastructure.isAllowed(Activity.CALL_BIDDER, payload);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isAllowedShouldEmitDataForDebug() {
        // given
        given(activityController.isAllowed(any())).willReturn(true);

        final ActivityInvocationPayload payload = ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, "bidder");

        // when
        final boolean result = infrastructure.isAllowed(Activity.CALL_BIDDER, payload);

        // then
        verify(debug).emitActivityInvocation(eq(Activity.CALL_BIDDER), same(payload));
        verify(debug).emitActivityInvocationResult(eq(Activity.CALL_BIDDER), same(payload), same(result));
    }

    @Test
    public void debugTraceShouldReturnSameTraceLog() {
        // given
        final List<ExtTraceActivityInfrastructure> trace = Collections.emptyList();
        given(debug.trace()).willReturn(trace);

        // when
        final List<ExtTraceActivityInfrastructure> debugTrace = infrastructure.debugTrace();

        // then
        assertThat(debugTrace).isSameAs(trace);
    }
}
