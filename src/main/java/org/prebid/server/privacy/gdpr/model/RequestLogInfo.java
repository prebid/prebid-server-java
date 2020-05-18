package org.prebid.server.privacy.gdpr.model;

import lombok.Value;
import org.prebid.server.auction.model.RequestType;

@Value(staticConstructor = "of")
public class RequestLogInfo {

    RequestType requestType;

    String refUrl;

    String accountId;
}
