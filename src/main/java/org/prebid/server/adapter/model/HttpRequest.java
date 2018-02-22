package org.prebid.server.adapter.model;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Packages together the fields needed to make an http request.
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class HttpRequest {

    String uri;

    MultiMap headers;

    BidRequest bidRequest;
}
