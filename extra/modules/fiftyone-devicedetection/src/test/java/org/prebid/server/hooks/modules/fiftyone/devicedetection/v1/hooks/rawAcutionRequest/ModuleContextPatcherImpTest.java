package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleContextPatcherImpTest {
    private static BiFunction<ModuleContext, Consumer<CollectedEvidence.CollectedEvidenceBuilder>, ModuleContext> buildPatcher()
    {
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                null,
                null,
                null,
                null
        ) {
            @Override
            public ModuleContext addEvidenceToContext(ModuleContext moduleContext, Consumer<CollectedEvidence.CollectedEvidenceBuilder> evidenceInjector) {
                return super.addEvidenceToContext(moduleContext, evidenceInjector);
            }
        }::addEvidenceToContext;
    }

    @Test
    public void shouldMakeNewContextIfNullIsPassedIn() {
        // given and when
        final ModuleContext newContext = buildPatcher().apply(null, b -> {});

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void shouldMakeNewEvidenceIfNoneWasPresent() {
        // given and when
        final ModuleContext newContext = buildPatcher().apply(
                ModuleContext.builder().build(),
                b -> {});

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void shouldMergeEvidences() {
        // given and when
        final String ua = "mad-hatter";
        final HashMap<String, String> sua = new HashMap<>();
        final ModuleContext existingContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .deviceUA(ua)
                        .build())
                .build();

        // when
        final ModuleContext newContext = buildPatcher().apply(
                existingContext,
                builder -> builder.secureHeaders(sua));

        // then
        assertThat(newContext).isNotNull();
        final CollectedEvidence newEvidence = newContext.collectedEvidence();
        assertThat(newEvidence).isNotNull();
        assertThat(newEvidence.deviceUA()).isEqualTo(ua);
        assertThat(newEvidence.secureHeaders()).isEqualTo(sua);
    }
}
