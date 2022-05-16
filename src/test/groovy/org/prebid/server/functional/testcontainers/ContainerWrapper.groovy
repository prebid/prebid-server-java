package org.prebid.server.functional.testcontainers

import groovy.transform.EqualsAndHashCode
import org.testcontainers.containers.GenericContainer

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

@EqualsAndHashCode(includes = ["uuid"])
class ContainerWrapper<T extends GenericContainer> {

    final UUID uuid
    final Map<String, String> configuration
    final Instant creationTime

    private final T container

    private boolean isRunning = false
    private ReentrantLock lock = new ReentrantLock(true)

    ContainerWrapper(T container, Map<String, String> configuration) {
        this.container = container
        this.configuration = configuration.asImmutable()
        this.creationTime = Instant.now()
        this.uuid = UUID.randomUUID()
    }

    void start() {
        guardWithLock {
            if (!isRunning) {
                container.start()
                isRunning = true
            }
        }
    }

    void stop() {
        guardWithLock {
            container.stop()
            container.getMappedPort(8080)
        }
    }

    int getMappedPort(int port) {
        guardWithLock {
            container.getMappedPort(port)
        }
    }

    String getHost() {
        guardWithLock {
            container.host
        }
    }

    private <T> T guardWithLock(Closure<T> closure) {
        try {
            lock.lock()
            closure()
        } finally {
            lock.unlock()
        }
    }
}
