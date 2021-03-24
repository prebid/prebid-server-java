package org.prebid.server.hooks.execution.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class HookStageExecutionResult<PAYLOAD> {

    boolean shouldReject;

    PAYLOAD payload;
}
