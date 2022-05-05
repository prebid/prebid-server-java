package org.prebid.server.bidder.adnuntius.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AdnuntiusResponse {

    @JsonProperty("adUnits")
    List<AdnuntiusAdsUnit> adsUnits;
}
