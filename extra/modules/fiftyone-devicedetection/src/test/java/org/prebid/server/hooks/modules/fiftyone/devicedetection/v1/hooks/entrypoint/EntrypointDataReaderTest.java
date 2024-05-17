package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.entrypoint;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EntrypointDataReaderTest {
    @Test
    public void shouldAddRawRequestHeaders() {
        // given
        final EntrypointPayload entrypointPayload = mock(EntrypointPayload.class);
        final String key = "ua";
        final String value = "AI-scape Imitator";
        when(entrypointPayload.headers()).thenReturn(CaseInsensitiveMultiMap.builder()
                .add(key, value)
                .build());

        // when
        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = CollectedEvidence.builder();
        new FiftyOneDeviceDetectionEntrypointHook()
                .entrypointEvidenceCollector
                .accept(evidenceBuilder, entrypointPayload);
        final CollectedEvidence evidence = evidenceBuilder.build();

        // then
        assertThat(evidence.rawHeaders().size()).isEqualTo(1);
        final Map.Entry<String, String> firstHeader = evidence.rawHeaders().stream().findFirst().get();
        assertThat(firstHeader.getKey()).isEqualTo(key);
        assertThat(firstHeader.getValue()).isEqualTo(value);
    }
}
