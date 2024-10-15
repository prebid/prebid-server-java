package org.prebid.server.hooks.modules.pb.request.correction.core.correction;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;

public interface Correction {

    BidRequest apply(Config config, BidRequest bidRequest);
}
