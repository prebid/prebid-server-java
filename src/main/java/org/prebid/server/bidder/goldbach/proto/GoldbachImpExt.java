package org.prebid.server.bidder.goldbach.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class GoldbachImpExt {

    @JsonProperty("goldbach")
    ExtImpGoldbachBidRequest extImp;
}
