package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence.CollectedEvidenceBuilder;

import java.util.function.Consumer;

@FunctionalInterface
public interface AdditionalEvidenceInjector extends Consumer<CollectedEvidenceBuilder> {
    default void injectInto(CollectedEvidenceBuilder evidenceBuilder) {
        accept(evidenceBuilder);
    }
}
