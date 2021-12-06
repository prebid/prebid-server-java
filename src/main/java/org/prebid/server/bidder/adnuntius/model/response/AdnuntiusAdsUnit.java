package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;

import java.util.List;

@Value
public class AdnuntiusAdsUnit {

    String auId;

    String targetId;

    String html;

    List<AdnuntiusAd> ads;
}
