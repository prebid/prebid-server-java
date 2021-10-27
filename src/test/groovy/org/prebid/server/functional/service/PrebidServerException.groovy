package org.prebid.server.functional.service

class PrebidServerException extends Exception {

    final int statusCode
    final String responseBody

    PrebidServerException(int statusCode, String message) {
        this.statusCode = statusCode
        this.responseBody = message
    }
}
