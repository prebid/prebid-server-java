package org.prebid.server.hooks.modules.pb.response.correction.core.correction;

import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;

import java.util.List;

public interface Correction {

    List<BidderResponse> apply(Config config, List<BidderResponse> bidderResponses);
}
