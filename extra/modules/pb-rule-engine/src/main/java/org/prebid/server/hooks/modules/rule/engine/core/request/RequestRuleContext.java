package org.prebid.server.hooks.modules.rule.engine.core.request;

import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;

@Value(staticConstructor = "of")
public class RequestRuleContext {

    AuctionContext auctionContext;

    Granularity granularity;

    String datacenter;
}
