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

    String getRootUri(int port) {
        "http://$host:${getMappedPort(port)}"
    }

    String getLogs() {
        guardWithLock {
            container.logs
        }
    }

    private def guardWithLock(Closure closure) {
        try {
            lock.lock()
            closure()
        } finally {
            lock.unlock()
        }
    }
}
