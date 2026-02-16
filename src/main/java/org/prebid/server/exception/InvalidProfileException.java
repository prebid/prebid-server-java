package org.prebid.server.exception;

import java.util.List;

public class InvalidProfileException extends RuntimeException {

    public InvalidProfileException(String message) {
        super(message);
    }

    public InvalidProfileException(List<String> messages) {
        super(String.join("\n", messages));
    }
}
