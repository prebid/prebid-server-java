package org.prebid.server.exception;

import lombok.Getter;

@SuppressWarnings("serial")
public class UnauthorizedAccountException extends RuntimeException {

    @Getter
    private String accountId;

    public UnauthorizedAccountException(String message, String accountId) {
        super(message);
        this.accountId = accountId;
    }
}
