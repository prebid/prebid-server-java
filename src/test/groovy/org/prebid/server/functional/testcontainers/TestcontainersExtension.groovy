package org.prebid.server.functional.testcontainers

import org.spockframework.runtime.extension.AbstractGlobalExtension

class TestcontainersExtension extends AbstractGlobalExtension {

    @Override
    void start() {
        Dependencies.start()
    }

    @Override
    void stop() {
        Dependencies.stop()
    }
}
