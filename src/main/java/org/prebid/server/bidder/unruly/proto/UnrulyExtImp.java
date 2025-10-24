package org.prebid.server.bidder.unruly.proto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpAuctionEnvironment;

@Value(staticConstructor = "of")
public class UnrulyExtImp<P, B> {

    P prebid;

    B bidder;

    String gpid;

    @JsonProperty("ae")
    @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
    ExtImpAuctionEnvironment auctionEnvironment;

    public static <P, B> UnrulyExtImp<P, B> of(P prebid, B bidder, String gpid) {
        return of(prebid, bidder, gpid, null);
    }

    public static <P, B> UnrulyExtImp<P, B> of(P prebid, B bidder) {
        return of(prebid, bidder, null, null);
    }
}
