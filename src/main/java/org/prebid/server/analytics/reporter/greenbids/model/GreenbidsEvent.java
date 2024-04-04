package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Value;
import org.prebid.server.analytics.model.AuctionEvent;

@Value(staticConstructor = "of")
public class GreenbidsEvent {
    String type;

    @JsonUnwrapped
    AuctionEvent auctionEvent;
}
