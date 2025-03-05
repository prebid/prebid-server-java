package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtRequestAlternateBidderCodesBidder {

    Boolean enabled;

    List<String> allowedBidderCodes;
}
