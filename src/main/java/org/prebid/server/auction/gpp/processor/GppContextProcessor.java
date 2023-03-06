package org.prebid.server.auction.gpp.processor;

import org.prebid.server.auction.gpp.model.GppContext;

public interface GppContextProcessor {

    GppContext process(GppContext gppContext);
}
