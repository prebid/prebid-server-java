package org.prebid.server.functional.testcontainers.container

import groovy.transform.EqualsAndHashCode
import org.testcontainers.images.builder.Transferable

@EqualsAndHashCode
class PbsContainerConfig {

    final Map<String, String> extraProperties
    final Map<String, Transferable> additionalFiles

    PbsContainerConfig(Map<?, ?> extraProperties = [:], Map<String, Transferable> additionalFiles = [:]) {
        this.extraProperties = normalizeProperties(extraProperties)
        this.additionalFiles = additionalFiles.asImmutable()
    }

    PbsContainerConfig plus(Map<?, ?> properties) {
        if (properties.values().any { it instanceof Transferable }) {
            throw new IllegalArgumentException("Use withFiles() to add container files")
        }
        new PbsContainerConfig(
                [:] << extraProperties << properties,
                additionalFiles
        )
    }

    PbsContainerConfig plus(PbsContainerConfig other) {
        new PbsContainerConfig(
                [:] << extraProperties << other.extraProperties,
                [:] << additionalFiles << other.additionalFiles
        )
    }

    PbsContainerConfig withFiles(Map<String, Transferable> files) {
        new PbsContainerConfig(
                extraProperties,
                [:] << additionalFiles << files
        )
    }

    private static Map<String, String> normalizeProperties(Map<?, ?> properties) {
        properties.collectEntries {
            key, value -> [(key.toString()): value?.toString()]
        }.asImmutable()
    }
}
