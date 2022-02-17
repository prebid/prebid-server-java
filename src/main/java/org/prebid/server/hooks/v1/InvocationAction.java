package org.prebid.server.hooks.v1;

public enum InvocationAction {

    NO_ACTION, UPDATE, REJECT;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
