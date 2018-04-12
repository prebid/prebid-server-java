package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;
import java.util.Map;

public class NoOpAdServerService implements AdServerService {

    @Override
    public Future<Map<String, Object>> buildAdServerKeyValues(RoutingContext context, BidRequest request) {
        return Future.succeededFuture(Collections.EMPTY_MAP);
    }

}
