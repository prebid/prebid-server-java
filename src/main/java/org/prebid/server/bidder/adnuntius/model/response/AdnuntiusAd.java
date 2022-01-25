package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AdnuntiusAd {

    AdnuntiusBid bid;

    String adId;

    String creativeWidth;

    String creativeHeight;

    String creativeId;

    String lineItemId;

    Map<String, String> destinationUrls;
}
