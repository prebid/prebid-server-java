package org.prebid.server.functional.testcontainers

import org.spockframework.runtime.extension.IGlobalExtension

class TestcontainersExtension implements IGlobalExtension {

    @Override
    void start() {
        Dependencies.start()
    }

    @Override
    void stop() {
        Dependencies.stop()
    }
}
