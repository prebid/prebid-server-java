package org.prebid.server.hooks.modules.pb.request.correction.v1.model;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.List;

@Accessors(fluent = true)
@Builder
@Value
public class InvocationResultImpl implements InvocationResult<AuctionRequestPayload> {

    InvocationStatus status;

    String message;

    InvocationAction action;

    PayloadUpdate<AuctionRequestPayload> payloadUpdate;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;

    Object moduleContext;

    Tags analyticsTags;
}
