package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;
import org.prebid.server.auction.aliases.AlternateBidderCodesConfig;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExtRequestPrebidAlternateBidderCodes implements AlternateBidderCodesConfig {

    Boolean enabled;

    Map<String, ExtRequestPrebidAlternateBidderCodesBidder> bidders;
}
