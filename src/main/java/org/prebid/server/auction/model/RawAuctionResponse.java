package org.prebid.server.auction.model;

import io.vertx.core.MultiMap;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.v1.exit.ExitpointPayload;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class RawAuctionResponse {

    String responseBody;

    MultiMap responseHeaders;

    public RawAuctionResponse of(ExitpointPayload exitpointPayload) {
        return this.toBuilder()
                .responseHeaders(exitpointPayload.responseHeaders())
                .responseBody(exitpointPayload.responseBody())
                .build();
    }
}
