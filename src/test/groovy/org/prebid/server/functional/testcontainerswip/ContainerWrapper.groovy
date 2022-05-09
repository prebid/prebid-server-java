package org.prebid.server.functional.testcontainerswip

import groovy.transform.EqualsAndHashCode

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

@EqualsAndHashCode(includes = ["uuid"])
class ContainerWrapper {

    UUID uuid = UUID.randomUUID()
    Map<String, String> configuration
    Instant creationTime

    private boolean isRunning = false
    private ReentrantLock lock = new ReentrantLock(true)

    ContainerWrapper(Map<String, String> configuration) {
        this.configuration = configuration
        this.creationTime = Instant.now()
    }

    void start() {
        guardWithLock {
            if (!isRunning) {
                println("starting $uuid container")
                Thread.sleep(1000)
                isRunning = true
            }
            println "$uuid container is running"
        }

    }

    void stop() {
        guardWithLock {
            println("stopping $uuid container")
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
