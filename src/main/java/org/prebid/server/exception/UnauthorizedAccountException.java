package org.prebid.server.exception;

import lombok.Getter;

@Getter
@SuppressWarnings("serial")
public class UnauthorizedAccountException extends RuntimeException {

    private String accountId;

    public UnauthorizedAccountException(String message, String accountId) {
        super(String.format("%s", message));
        this.accountId = accountId;
    }
}
