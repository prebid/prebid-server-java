package org.prebid.server.exception;

import org.prebid.server.hooks.execution.model.HookExecutionContext;

public class RejectedBidRequestException extends RuntimeException {

    private final HookExecutionContext hookExecutionContext;

    public RejectedBidRequestException(HookExecutionContext hookExecutionContext) {
        this.hookExecutionContext = hookExecutionContext;
    }

    public HookExecutionContext getHookExecutionContext() {
        return hookExecutionContext;
    }
}
