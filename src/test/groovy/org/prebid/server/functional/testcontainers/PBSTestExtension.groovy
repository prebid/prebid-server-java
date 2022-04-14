package org.prebid.server.functional.testcontainers

import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.SpecInfo

class PBSTestExtension implements IAnnotationDrivenExtension<PBSTest> {

    @Override
    void visitSpecAnnotation(PBSTest annotation, SpecInfo spec) {
        spec.addListener(new ErrorListener())
    }
}
