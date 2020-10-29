package org.prebid.server.proto.response.legacy;

import lombok.Builder;
import lombok.Value;

@Deprecated
@Builder
@Value
public class BidderDebug {

    String requestUri;

    String requestBody;

    String responseBody;

    Integer statusCode;
}
