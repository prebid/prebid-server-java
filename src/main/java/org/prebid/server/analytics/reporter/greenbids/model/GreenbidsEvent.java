package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Value;

@Value(staticConstructor = "of")
public class GreenbidsEvent<T> {
    String type;

    //@JsonUnwrapped
    //AuctionEvent auctionEvent;

    @JsonUnwrapped
    T event;
}
