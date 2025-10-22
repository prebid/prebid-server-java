package org.prebid.server.proto.openrtb.ext.request.nativery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class BidExtNativery {

    @JsonProperty("bid_ad_media_type")
    String bidAdMediaType;

    @JsonProperty("bid_adv_domains")
    List<String> bidAdvDomains;
}
