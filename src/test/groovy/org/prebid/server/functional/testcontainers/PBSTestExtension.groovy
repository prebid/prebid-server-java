package org.prebid.server.functional.testcontainers

import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.SpecInfo

class PBSTestExtension extends AbstractAnnotationDrivenExtension<PBSTest> {

    @Override
    void visitSpecAnnotation(PBSTest annotation, SpecInfo spec) {
        spec.addListener(new ErrorListener())
    }
}
