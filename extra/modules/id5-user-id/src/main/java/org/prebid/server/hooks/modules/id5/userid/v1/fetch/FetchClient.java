package org.prebid.server.hooks.modules.id5.userid.v1.fetch;

import io.vertx.core.Future;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public interface FetchClient {

    Future<Id5UserId> fetch(long partnerId, AuctionRequestPayload payload,
                            AuctionInvocationContext invocationContext);
}
