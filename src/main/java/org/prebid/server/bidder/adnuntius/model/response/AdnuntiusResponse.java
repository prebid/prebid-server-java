package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;

import java.util.List;

@Value
public class AdnuntiusResponse {

    List<AdnuntiusAdsUnit> adUnits;
}
