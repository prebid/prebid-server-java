package org.prebid.server.functional.util

import org.testcontainers.images.builder.Transferable

class ContainerFileUtil implements ObjectMapperWrapper {

    static Map<String, Transferable> yamlFile(String path, Object content) {
        [(path): Transferable.of(encodeYaml(content))]
    }

    static Map<String, Transferable> jsonFile(String path, Object content) {
        [(path): Transferable.of(encode(content))]
    }

    static Map<String, Transferable> emptyFolders(String... paths) {
        paths.collectEntries { path ->
            [("$path/.keep".toString()): Transferable.of(new byte[0], 010755)]
        }
    }
}
