package org.prebid.server.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class InvalidStoredImpException extends RuntimeException {

    @Getter
    private final List<String> messages;

    public InvalidStoredImpException(String message) {
        super(message);
        this.messages = Collections.singletonList(message);
    }
}
