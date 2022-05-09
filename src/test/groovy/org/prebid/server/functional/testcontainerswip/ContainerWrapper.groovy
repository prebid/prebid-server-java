package org.prebid.server.functional.testcontainerswip

import groovy.transform.EqualsAndHashCode

import java.time.Instant

@EqualsAndHashCode(includes = ["uuid"])
class ContainerWrapper {

    UUID uuid = UUID.randomUUID()
    Map<String, String> configuration
    Instant creationTime

    ContainerWrapper(Map<String, String> configuration) {
        this.configuration = configuration
        this.creationTime = Instant.now()
    }

    void start() {
        println("starting $uuid container")
    }

    void stop() {
        println("stopping $uuid container")
    }
}
