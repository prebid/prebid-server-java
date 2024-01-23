package org.prebid.server.bidder.adquery.model.response;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Value(staticConstructor = "of")
public class AdQueryMediaType {

    BidType name;

    String width;

    String height;
}
