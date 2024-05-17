package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import io.vertx.core.Future;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class FiftyOneDeviceDetectionEntrypointHookTest {
    private static EntrypointHook buildHook(
            BiConsumer<CollectedEvidence.CollectedEvidenceBuilder, EntrypointPayload> evidenceCollector)
    {
        final FiftyOneDeviceDetectionEntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook();
        hook.entrypointEvidenceCollector = evidenceCollector;
        return hook;
    }

    @Test
    public void codeShouldStartWithModuleCode() {
        // given
        final EntrypointHook hook = buildHook(null);

        // when and then
        assertThat(hook.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    @Test
    public void shouldPassPayloadAndBuilderThroughModulePatcher() {
        // given
        final EntrypointPayload payload = mock(EntrypointPayload.class);

        // when
        final boolean[] payloadReceived = { false };
        final EntrypointHook hook = buildHook(((evidenceBuilder, entrypointPayload) -> {
            assertThat(entrypointPayload).isEqualTo(payload);
            payloadReceived[0] = true;
        }));
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(payload, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(payloadReceived).containsExactly(true);
    }

    @Test
    public void shouldReturnPatchedModule() {
        // given and when
        final boolean[] builderPatcherCalled = { false };
        final EntrypointHook hook = buildHook(
                (evidenceBuilder, entrypointPayload) -> builderPatcherCalled[0] = true);
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(null, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(builderPatcherCalled).containsExactly(true);
        assertThat(result.result().moduleContext()).isNotNull();
    }
}
