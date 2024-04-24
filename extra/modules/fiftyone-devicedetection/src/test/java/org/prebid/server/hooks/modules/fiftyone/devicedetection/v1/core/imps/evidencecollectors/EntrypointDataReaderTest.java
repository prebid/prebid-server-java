package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;
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
        new EntrypointDataReader().evidenceFrom(entrypointPayload).injectInto(evidenceBuilder);
        final CollectedEvidence evidence = evidenceBuilder.build();

        // then
        assertThat(evidence.rawHeaders().size()).isEqualTo(1);
        final Map.Entry<String, String> firstHeader = evidence.rawHeaders().stream().findFirst().get();
        assertThat(firstHeader.getKey()).isEqualTo(key);
        assertThat(firstHeader.getValue()).isEqualTo(value);
    }
}
