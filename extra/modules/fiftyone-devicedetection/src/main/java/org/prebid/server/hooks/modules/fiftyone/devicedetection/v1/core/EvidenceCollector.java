package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence.CollectedEvidenceBuilder;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface EvidenceCollector <T> extends BiConsumer<CollectedEvidenceBuilder, T> {
    default AdditionalEvidenceInjector evidenceFrom(T availableData) {
        return evidenceBuilder -> accept(evidenceBuilder, availableData);
    }
}
