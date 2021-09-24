package org.prebid.server.hooks.modules.ortb2.blocking.v1.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidderRequestPayloadImpl implements BidderRequestPayload {

    BidRequest bidRequest;
}
