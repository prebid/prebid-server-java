package org.prebid.server.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("serial")
public class InvalidRequestException extends RuntimeException {

    @Getter
    private final List<String> messages;

    public InvalidRequestException(String message) {
        super(message);
        this.messages = Collections.singletonList(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
        this.messages = Collections.singletonList(message);
    }

    public InvalidRequestException(List<String> messages) {
        super(String.join("\n", messages));
        this.messages = messages;
    }
}
