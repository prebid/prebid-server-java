package org.prebid.server.proto.response;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BidderDebug {

    String requestUri;

    String requestBody;

    String responseBody;

    Integer statusCode;
}
