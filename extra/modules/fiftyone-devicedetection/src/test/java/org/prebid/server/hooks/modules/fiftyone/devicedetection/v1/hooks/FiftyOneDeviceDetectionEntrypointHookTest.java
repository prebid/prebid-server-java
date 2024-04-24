package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import io.vertx.core.Future;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.ModuleContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class FiftyOneDeviceDetectionEntrypointHookTest {
    @Test
    public void codeShouldStartWithModuleCode() {
        // given
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook(null, null);

        // when and then
        assertThat(hook.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    @Test
    public void shouldPassPayloadAndBuilderThroughModulePatcher() {
        // given
        final EntrypointPayload payload = mock(EntrypointPayload.class);
        final CollectedEvidence.CollectedEvidenceBuilder builder = CollectedEvidence.builder();

        // when
        final boolean[] payloadReceived = { false };
        final boolean[] patcherCalled = { false };
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook(((evidenceBuilder, entrypointPayload) -> {
            assertThat(evidenceBuilder).isEqualTo(builder);
            assertThat(entrypointPayload).isEqualTo(payload);
            payloadReceived[0] = true;
        }), (moduleContext, collectedEvidenceBuilderConsumer) -> {
            assertThat(moduleContext).isNull();
            patcherCalled[0] = true;
            collectedEvidenceBuilderConsumer.injectInto(builder);
            return null;
        });
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(payload, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(patcherCalled).containsExactly(true);
        assertThat(payloadReceived).containsExactly(true);
    }

    @Test
    public void shouldReturnPatchedModule() {
        // given
        final ModuleContext newModuleContext = ModuleContext.builder().build();

        // when
        final boolean[] patcherCalled = { false };
        final EntrypointHook hook = new FiftyOneDeviceDetectionEntrypointHook(
                (evidenceBuilder, entrypointPayload) -> fail("Evidence builder should not be called"),
                (moduleContext, collectedEvidenceBuilderConsumer) -> {
                    patcherCalled[0] = true;
                    return newModuleContext;
                });
        final Future<InvocationResult<EntrypointPayload>> result = hook.call(null, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(patcherCalled).containsExactly(true);
        assertThat(result.result().moduleContext()).isEqualTo(newModuleContext);
    }
}
