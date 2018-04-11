package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public interface AdServerService {

    Future<Map<String, String>> buildAdServerKeyValues(RoutingContext context, BidRequest request);

}
