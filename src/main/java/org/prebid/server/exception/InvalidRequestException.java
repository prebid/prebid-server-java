package org.prebid.server.exception;

import lombok.Getter;
import org.prebid.server.cookie.exception.CookieSyncException;

import java.util.Collections;
import java.util.List;

public class InvalidRequestException extends CookieSyncException {

    @Getter
    private final List<String> messages;

    public InvalidRequestException(String message) {
        super(message, null);
        this.messages = Collections.singletonList(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause, null);
        this.messages = Collections.singletonList(message);
    }

    public InvalidRequestException(List<String> messages) {
        super(String.join("\n", messages), null);
        this.messages = messages;
    }
}
