package org.prebid.server.functional.model.bidder

enum CompressionType {

    NONE, GZIP

    String getValue() {
        name().toLowerCase()
    }
}
