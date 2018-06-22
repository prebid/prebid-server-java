package org.prebid.server.exception;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InvalidRequestException extends RuntimeException {
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
        super(messages.stream().collect(Collectors.joining("\n")));
        this.messages = messages;
    }

    public List<String> getMessages() {
        return messages;
    }
}
