package org.prebid.server.hooks.execution.model;

import lombok.Value;
import org.prebid.server.auction.model.Rejected;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class HookStageExecutionResult<PAYLOAD> {

    boolean shouldReject;

    PAYLOAD payload;

    Map<String, List<Rejected>> rejections;

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> success(PAYLOAD payload,
                                                                      Map<String, List<Rejected>> rejections) {
        return of(false, payload, rejections);
    }

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> success(PAYLOAD payload) {
        return of(false, payload, Collections.emptyMap());
    }

    public static <PAYLOAD> HookStageExecutionResult<PAYLOAD> reject() {
        return of(true, null, Collections.emptyMap());
    }
}
