package org.prebid.server.exception;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("serial")
public class InvalidRequestException extends RuntimeException {

    private final List<String> messages;

    private final boolean needEnhancedLogging;

    public InvalidRequestException(String message) {
        super(message);
        this.messages = Collections.singletonList(message);
        this.needEnhancedLogging = false;
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
        this.messages = Collections.singletonList(message);
        this.needEnhancedLogging = false;
    }

    public InvalidRequestException(String message, boolean needEnhancedLogging) {
        super(message);
        this.messages = Collections.singletonList(message);
        this.needEnhancedLogging = needEnhancedLogging;
    }

    public InvalidRequestException(List<String> messages) {
        super(String.join("\n", messages));
        this.messages = messages;
        this.needEnhancedLogging = false;
    }

    public InvalidRequestException(List<String> messages, boolean needEnhancedLogging) {
        super(String.join("\n", messages));
        this.messages = messages;
        this.needEnhancedLogging = needEnhancedLogging;
    }

    public List<String> getMessages() {
        return messages;
    }

    public boolean isNeedEnhancedLogging() {
        return needEnhancedLogging;
    }
}
