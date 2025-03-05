package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class ExtRequestPrebidAlternateBidderCodes {

    Boolean enabled;

    Map<String, ExtRequestPrebidAlternateBidderCodesBidder> bidders;
}
