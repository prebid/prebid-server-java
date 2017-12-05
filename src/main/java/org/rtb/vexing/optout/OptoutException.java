package org.rtb.vexing.optout;

class OptoutException extends RuntimeException {

    OptoutException(String message) {
        super(message);
    }

    OptoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
