package org.prebid.server.bidder.resetdigital.request;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResetDigitalSite {

    String domain;

    String referrer;
}
