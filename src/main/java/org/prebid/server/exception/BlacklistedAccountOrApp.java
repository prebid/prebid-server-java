package org.prebid.server.exception;

public class BlacklistedAccountOrApp extends RuntimeException {

    private final boolean isAccount;

    public BlacklistedAccountOrApp(String message, boolean isAccount) {
        super(message);
        this.isAccount = isAccount;
    }

    public boolean isAccount() {
        return isAccount;
    }
}
