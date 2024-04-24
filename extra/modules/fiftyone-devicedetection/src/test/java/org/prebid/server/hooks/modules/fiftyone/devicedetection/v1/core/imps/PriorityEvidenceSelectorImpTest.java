package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityEvidenceSelectorImpTest {
    @Test
    public void shouldSelectSuaIfPresent() {
        // given
        final UserAgent userAgent = UserAgent.builder().build();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceSUA(userAgent)
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();
        final Map<String, String> suaHeaders = Collections.singletonMap("ua", "fake-ua");

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, evidence) -> {
            assertThat(sua).isEqualTo(userAgent);
            converterCalled[0] = true;
            evidence.putAll(suaHeaders);
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        assertThat(converterCalled).containsExactly(true);
        assertThat(evidence).isNotSameAs(suaHeaders);
        assertThat(evidence).containsExactlyEntriesOf(suaHeaders);
    }

    @Test
    public void shouldSelectUaIfNoSuaPresent() {
        // given
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("dummy-ua")
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, e) -> {
            converterCalled[0] = true;
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        assertThat(evidence.size()).isEqualTo(1);
        final Map.Entry<String, String> evidenceFragment = evidence.entrySet().stream().findFirst().get();
        assertThat(evidenceFragment.getKey()).isEqualTo("header.user-agent");
        assertThat(evidenceFragment.getValue()).isEqualTo(collectedEvidence.deviceUA());
        assertThat(converterCalled).containsExactly(true);
    }
    @Test
    public void shouldMergeUaWithSuaIfBothPresent() {
        // given
        final UserAgent userAgent = UserAgent.builder().build();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceSUA(userAgent)
                .deviceUA("dummy-ua")
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();
        final Map<String, String> suaHeaders = Collections.singletonMap("ua", "fake-ua");

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, evidence) -> {
            assertThat(sua).isEqualTo(userAgent);
            converterCalled[0] = true;
            evidence.putAll(suaHeaders);
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        assertThat(converterCalled).containsExactly(true);
        assertThat(evidence).isNotEqualTo(suaHeaders);
        assertThat(evidence).containsAllEntriesOf(suaHeaders);
        assertThat(evidence).containsEntry("header.user-agent", collectedEvidence.deviceUA());
        assertThat(evidence.size()).isEqualTo(suaHeaders.size() + 1);
    }

    @Test
    public void shouldSelectRawHeaderIfNoDeviceInfoPresent() {
        // given
        final List<Map.Entry<String, String>> rawHeaders = List.of(
                new AbstractMap.SimpleEntry<>("ua", "zumba"),
                new AbstractMap.SimpleEntry<>("sec-ua", "astrolabe")
        );
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .rawHeaders(rawHeaders)
                .build();

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, e) -> {
            converterCalled[0] = true;
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        final List<Map.Entry<String, String>> evidenceFragments = evidence.entrySet().stream().toList();
        assertThat(evidenceFragments.size()).isEqualTo(rawHeaders.size());
        for (int i = 0, n = rawHeaders.size(); i < n; ++i) {
            final Map.Entry<String, String> rawEntry = rawHeaders.get(i);
            final Map.Entry<String, String> newEntry = evidenceFragments.get(i);
            assertThat(newEntry.getKey()).isEqualTo("header." + rawEntry.getKey());
            assertThat(newEntry.getValue()).isEqualTo(rawEntry.getValue());
        }
        assertThat(converterCalled).containsExactly(true);
    }

    @Test
    public void shouldPickLastHeaderWithSameKey() {
        // given
        final String theKey = "ua";
        final List<Map.Entry<String, String>> rawHeaders = List.of(
                new AbstractMap.SimpleEntry<>(theKey, "zumba"),
                new AbstractMap.SimpleEntry<>(theKey, "astrolabe")
        );
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .rawHeaders(rawHeaders)
                .build();

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, e) -> {
            converterCalled[0] = true;
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        final List<Map.Entry<String, String>> evidenceFragments = evidence.entrySet().stream().toList();
        assertThat(evidenceFragments.size()).isEqualTo(1);
        assertThat(evidenceFragments.get(0).getValue()).isEqualTo(rawHeaders.get(1).getValue());
        assertThat(converterCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnEmptyMapOnNoEvidenceToPick() {
        // given
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] converterCalled = { false };
        final PriorityEvidenceSelector evidenceSelector = new PriorityEvidenceSelectorImp((sua, e) -> {
            converterCalled[0] = true;
        });
        final Map<String, String> evidence = evidenceSelector.pickRelevantFrom(collectedEvidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence).isEmpty();
        assertThat(converterCalled).containsExactly(true);
    }
}
