package org.prebid.server.hooks.execution.v1;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.auction.model.Rejected;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;
import java.util.Map;

@Accessors(fluent = true)
@Builder
@Value
public class InvocationResultImpl<PAYLOAD> implements InvocationResult<PAYLOAD> {

    InvocationStatus status;

    String message;

    InvocationAction action;

    PayloadUpdate<PAYLOAD> payloadUpdate;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;

    Map<String, List<Rejected>> rejections;

    Object moduleContext;

    Tags analyticsTags;
}
