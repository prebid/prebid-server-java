package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FiftyOneDeviceDetectionEntrypointHookTest {
    private EntrypointHook target;

    @Before
    public void setUp() {
        target = new FiftyOneDeviceDetectionEntrypointHook();
    }

    @Test
    public void codeShouldStartWithModuleCode() {
        // when and then
        assertThat(target.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    @Test
    public void callShouldReturnPatchedModule() {
        // given
        final EntrypointPayload entrypointPayload = EntrypointPayloadImpl.of(
                null,
                CaseInsensitiveMultiMap.builder().build(),
                null
        );

        // when
        final Future<InvocationResult<EntrypointPayload>> result = target.call(entrypointPayload, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().moduleContext()).isNotNull();
    }

    @Test
    public void callShouldAddRawRequestHeadersToModuleEvidence() {
        // given
        final String key = "ua";
        final String value = "AI-scape Imitator";
        final EntrypointPayload entrypointPayload = EntrypointPayloadImpl.of(
                null,
                CaseInsensitiveMultiMap.builder()
                        .add(key, value)
                        .build(),
                null
        );

        // when
        final Future<InvocationResult<EntrypointPayload>> result = target.call(entrypointPayload, null);

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
