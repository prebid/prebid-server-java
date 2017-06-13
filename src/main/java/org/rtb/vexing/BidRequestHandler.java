package org.rtb.vexing;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;

import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static org.rtb.vexing.Application.NO_BID_RESPONSE;

public final class BidRequestHandler {

    static Future clientBid(HttpClient client, Bidder bidder, PreBidRequest request) {
        Imp imp = Imp.builder()
                     .id(bidder.bid_id)
                     .banner(Banner.builder().format(request.ad_units.get(0).sizes).build())
                     .build();
        BidRequest bidRequest = BidRequest.builder()
                                          .id(bidder.bid_id)
                                          .app(request.app)
                                          .device(request.device)
                                          .imp(Collections.singletonList(imp))
                                          .build();

        Future<BidResponse> future = Future.future();
        try {
            URL url = new URL(bidder.bidder_code);
            client.post(url.getPort(), url.getHost(), url.getFile(), clientResponseHandler(future))
                  .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                  .setTimeout(request.timeout_millis)
                  .exceptionHandler(throwable -> future.complete(NO_BID_RESPONSE))
                  .end(Json.encode(bidRequest));
        } catch (MalformedURLException e) {
            future.complete(NO_BID_RESPONSE);
        }
        return future;
    }

    private static Handler<HttpClientResponse> clientResponseHandler(Future<BidResponse> future) {
        return response -> {
            if (response.statusCode() == 200)
                response.bodyHandler(buffer -> future.complete(Json.decodeValue(buffer.toString(), BidResponse.class)));
            else
                future.complete(NO_BID_RESPONSE);
        };
    }
}
