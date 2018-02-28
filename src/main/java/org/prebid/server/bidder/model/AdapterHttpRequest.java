package org.prebid.server.bidder.model;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class AdapterHttpRequest {

    String uri;

    MultiMap headers;

    BidRequest bidRequest;
}
