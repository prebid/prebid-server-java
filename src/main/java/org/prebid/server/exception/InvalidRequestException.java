package org.prebid.server.exception;

import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object other) {
        return other.getClass() == this.getClass()
                && CollectionUtils.isEqualCollection(messages, ((InvalidRequestException) other).messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages.toArray());
    }
}
