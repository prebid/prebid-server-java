package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.AuctionRequestHeadersContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WURFLDeviceDetectionEntrypointHookTest {

    private EntrypointPayload payload;
    private InvocationContext context;

    @BeforeEach
    void setUp() {
        payload = mock(EntrypointPayload.class);
        context = mock(InvocationContext.class);
    }

    @Test
    public void codeShouldReturnCorrectHookCode() {

        // given
        final WURFLDeviceDetectionEntrypointHook target = new WURFLDeviceDetectionEntrypointHook();

        // when
        final String result = target.code();

        // then
        assertThat(result).isEqualTo("wurfl-devicedetection-entrypoint-hook");
    }

    @Test
    public void callShouldReturnSuccessWithNoAction() {
        // given
        final WURFLDeviceDetectionEntrypointHook target = new WURFLDeviceDetectionEntrypointHook();
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test")
                .build();
        when(payload.headers()).thenReturn(headers);

        // when
        final Future<InvocationResult<EntrypointPayload>> result = target.call(payload, context);

        // then
        assertThat(result).isNotNull();
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<EntrypointPayload> invocationResult = result.result();
        assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
        assertThat(invocationResult.moduleContext()).isNotNull();
    }

    @Test
    public void callShouldHandleNullHeaders() {
        // given
        final WURFLDeviceDetectionEntrypointHook target = new WURFLDeviceDetectionEntrypointHook();

        // when
        when(payload.headers()).thenReturn(null);
        final Future<InvocationResult<EntrypointPayload>> result = target.call(payload, context);

        // then
        assertThat(result).isNotNull();
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<EntrypointPayload> invocationResult = result.result();
        assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
        assertThat(invocationResult.moduleContext()).isNotNull();
        assertThat(invocationResult.moduleContext() instanceof AuctionRequestHeadersContext).isTrue();
    }
}
