package org.prebid.server.hooks.v1;

public enum InvocationAction {

    NO_ACTION, ACTION, REJECT;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
