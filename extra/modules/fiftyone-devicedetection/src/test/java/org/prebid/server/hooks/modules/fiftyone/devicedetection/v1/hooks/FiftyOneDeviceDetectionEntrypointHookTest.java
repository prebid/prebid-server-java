package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import io.vertx.core.Future;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FiftyOneDeviceDetectionEntrypointHookTest {
    @Test
    public void codeShouldStartWithModuleCode() {
        // given
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook();

        // when and then
        assertThat(hook.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    @Test
    public void shouldReturnPatchedModule() {
        // given and when
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook();
        final EntrypointPayload entrypointPayload = mock(EntrypointPayload.class);
        when(entrypointPayload.headers()).thenReturn(CaseInsensitiveMultiMap.builder().build());
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(entrypointPayload, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().moduleContext()).isNotNull();
    }

    @Test
    public void shouldAddRawRequestHeaders() {
        // given
        final EntrypointPayload entrypointPayload = mock(EntrypointPayload.class);
        final String key = "ua";
        final String value = "AI-scape Imitator";
        when(entrypointPayload.headers()).thenReturn(CaseInsensitiveMultiMap.builder()
                .add(key, value)
                .build());

        // given and when
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook();
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(entrypointPayload, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().moduleContext()).isInstanceOf(ModuleContext.class);
        final CollectedEvidence evidence = ((ModuleContext) result.result().moduleContext()).collectedEvidence();
        assertThat(evidence).isNotNull();
        assertThat(evidence.rawHeaders()).hasSize(1);
        final Map.Entry<String, String> firstHeader = evidence.rawHeaders().stream().findFirst().get();
        assertThat(firstHeader.getKey()).isEqualTo(key);
        assertThat(firstHeader.getValue()).isEqualTo(value);
    }
}
