package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model;

import lombok.Builder;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

@Builder
public record InvocationResultImpl<PAYLOAD>(
    InvocationStatus status,
    String message,
    InvocationAction action,
    PayloadUpdate<PAYLOAD> payloadUpdate,
    List<String> errors,
    List<String> warnings,
    List<String> debugMessages,
    Object moduleContext,
    Tags analyticsTags
) implements InvocationResult<PAYLOAD> {
}
