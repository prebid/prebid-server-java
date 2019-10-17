package org.prebid.server.bidder.sharethrough.model.bidResponse;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpSharethroughCreativeMetadata {

    String campaignKey;

    String creativeKey;

    String dealId;
}

