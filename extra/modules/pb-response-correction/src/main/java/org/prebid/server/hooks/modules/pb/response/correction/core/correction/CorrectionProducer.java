package org.prebid.server.hooks.modules.pb.response.correction.core.correction;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;

public interface CorrectionProducer {

    boolean shouldProduce(Config config, BidRequest bidRequest);

    Correction produce();
}
