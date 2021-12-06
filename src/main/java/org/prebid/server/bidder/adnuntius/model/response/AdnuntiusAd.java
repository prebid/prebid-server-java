package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;

import java.util.Map;

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
