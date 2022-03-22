package org.prebid.server.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class InvalidStoredRequestException extends RuntimeException {

    @Getter
    private final List<String> messages;

    public InvalidStoredRequestException(String message) {
        super(message);
        this.messages = Collections.singletonList(message);
    }

    public InvalidStoredRequestException(List<String> messages) {
        super(String.join("\n", messages));
        this.messages = messages;
    }
}
