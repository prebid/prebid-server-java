package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.ModuleContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleContextPatcherImpTest {
    @Test
    public void shouldMakeNewContextIfNullIsPassedIn() {
        // given and when
        final ModuleContext newContext = new ModuleContextPatcherImp().contextWithNewEvidence(null, b -> {});

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void shouldMakeNewEvidenceIfNoneWasPresent() {
        // given and when
        final ModuleContext newContext = new ModuleContextPatcherImp().contextWithNewEvidence(
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
        final UserAgent sua = UserAgent.builder().build();
        final ModuleContext existingContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .deviceUA(ua)
                        .build())
                .build();

        // when
        final ModuleContext newContext = new ModuleContextPatcherImp().contextWithNewEvidence(
                existingContext,
                builder -> builder.deviceSUA(sua));

        // then
        assertThat(newContext).isNotNull();
        final CollectedEvidence newEvidence = newContext.collectedEvidence();
        assertThat(newEvidence).isNotNull();
        assertThat(newEvidence.deviceUA()).isEqualTo(ua);
        assertThat(newEvidence.deviceSUA()).isEqualTo(sua);
    }
}
