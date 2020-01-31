package org.prebid.server.exception;

import lombok.Getter;

@Getter
@SuppressWarnings("serial")
public class UnauthorizedAccountException extends RuntimeException {

    private String userId;

    public UnauthorizedAccountException(String message, String userId) {
        super(String.format("%s %s", message, userId));
        this.userId = userId;
    }
}
