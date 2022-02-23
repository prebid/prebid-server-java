package org.prebid.server.hooks.v1;

public enum InvocationStatus {

    SUCCESS, FAILURE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
