package org.prebid.server.exception;

import java.util.List;

public class InvalidStoredRequestException extends RuntimeException {

    public InvalidStoredRequestException(String message) {
        super(message);
    }

    public InvalidStoredRequestException(List<String> messages) {
        super(String.join("\n", messages));
    }
}
