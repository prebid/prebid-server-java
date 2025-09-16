package org.prebid.server.hooks.execution.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class HookStageExecutionResult<PAYLOAD> {

    boolean shouldReject;

    PAYLOAD payload;

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> success(PAYLOAD payload) {
        return of(false, payload);
    }

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> reject() {
        return of(true, null);
    }
}
