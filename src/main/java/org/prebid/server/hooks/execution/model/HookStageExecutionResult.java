package org.prebid.server.hooks.execution.model;

import lombok.Value;
import org.prebid.server.auction.model.RejectionResult;

@Value(staticConstructor = "of")
public class HookStageExecutionResult<PAYLOAD> {

    RejectionResult rejectionResult;

    PAYLOAD payload;

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> success(PAYLOAD payload) {
        return of(RejectionResult.allowed(), payload);
    }

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> reject(RejectionResult.Rejected rejected) {
        return of(rejected, null);
    }
}
