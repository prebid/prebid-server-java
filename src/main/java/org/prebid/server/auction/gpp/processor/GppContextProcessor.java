package org.prebid.server.auction.gpp.processor;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextWrapper;

public interface GppContextProcessor {

    GppContextWrapper process(GppContext gppContext);
}
