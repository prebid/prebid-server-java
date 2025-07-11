package org.prebid.server.hooks.modules.rule.engine.core.request.context;

import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;

@Value(staticConstructor = "of")
public class RequestResultContext {

    AuctionContext auctionContext;

    Granularity granularity;
}
