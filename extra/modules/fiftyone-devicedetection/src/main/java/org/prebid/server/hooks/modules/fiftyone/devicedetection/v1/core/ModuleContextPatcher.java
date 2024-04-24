package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.ModuleContext;

import java.util.function.BiFunction;

@FunctionalInterface
public interface ModuleContextPatcher extends BiFunction<ModuleContext, AdditionalEvidenceInjector, ModuleContext> {
    default ModuleContext contextWithNewEvidence(
            ModuleContext existingContext,
            AdditionalEvidenceInjector newEvidenceInjector)
    {
        return apply(existingContext, newEvidenceInjector);
    }
}
