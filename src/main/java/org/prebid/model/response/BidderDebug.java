package org.prebid.model.response;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class BidderDebug {

    String requestUri;

    String requestBody;

    String responseBody;

    Integer statusCode;
}
