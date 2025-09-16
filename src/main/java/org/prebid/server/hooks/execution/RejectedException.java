package org.prebid.server.hooks.execution;

class RejectedException extends RuntimeException {

    private final Object result;

    RejectedException(Object result) {
        this.result = result;
    }

    @SuppressWarnings("unchecked")
    public <T> T result() {
        return (T) result;
    }
}
