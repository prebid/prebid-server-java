package org.prebid.server.handler.openrtb2;

import io.vertx.core.MultiMap;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;

@Value
@Builder(toBuilder = true)
public class RawResponseContext {

    AuctionContext auctionContext;

    String responseBody;

    MultiMap responseHeaders;
}
