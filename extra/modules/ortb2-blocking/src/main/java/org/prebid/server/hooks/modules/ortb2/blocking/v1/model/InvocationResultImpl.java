package org.prebid.server.hooks.modules.ortb2.blocking.v1.model;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.modules.ortb2.blocking.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

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

    ModuleContext moduleContext;

    Tags analyticsTags;
}
