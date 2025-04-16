package org.prebid.server.hooks.v1;

import org.prebid.server.auction.model.Rejected;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;
import java.util.Map;

public interface InvocationResult<PAYLOAD> {

    InvocationStatus status();

    String message();

    InvocationAction action();

    PayloadUpdate<PAYLOAD> payloadUpdate();

    List<String> errors();

    List<String> warnings();

    Map<String, List<Rejected>> rejections();

    List<String> debugMessages();

    Object moduleContext();

    Tags analyticsTags();
}
