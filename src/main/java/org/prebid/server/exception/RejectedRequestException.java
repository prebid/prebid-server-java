package org.prebid.server.exception;

import org.prebid.server.hooks.execution.model.HookExecutionContext;

public class RejectedRequestException extends RuntimeException {

    private final HookExecutionContext hookExecutionContext;
    private final boolean debugEnabled;

    public RejectedRequestException(HookExecutionContext hookExecutionContext, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.hookExecutionContext = hookExecutionContext;
    }

    public RejectedRequestException(HookExecutionContext hookExecutionContext) {
        this(hookExecutionContext, false);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public HookExecutionContext getHookExecutionContext() {
        return hookExecutionContext;
    }
}
