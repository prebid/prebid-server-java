package org.prebid.server.exception;

import org.prebid.server.hooks.execution.model.HookExecutionContext;

public class RejectedRequestException extends RuntimeException {

    private final HookExecutionContext hookExecutionContext;

    public RejectedRequestException(HookExecutionContext hookExecutionContext) {
        this.hookExecutionContext = hookExecutionContext;
    }

    public HookExecutionContext getHookExecutionContext() {
        return hookExecutionContext;
    }
}
