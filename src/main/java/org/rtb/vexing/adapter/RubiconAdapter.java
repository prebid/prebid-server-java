package org.rtb.vexing.adapter;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

public class RubiconAdapter implements Adapter {

    private final String endpoint;
    private final String usersyncUrl;
    private final String xapiUsername;
    private final String xapiPassword;

    public RubiconAdapter(JsonObject config) {
        endpoint = config.getString("endpoint");
        usersyncUrl = config.getString("usersync_url");
        xapiUsername = config.getJsonObject("XAPI").getString("Username");
        xapiPassword = config.getJsonObject("XAPI").getString("Password");
    }

    @Override
    public Future<BidResponse> clientBid(HttpClient client, Bidder bidder, PreBidRequest request) {
        Imp imp = Imp.builder()
                .id(bidder.bidId)
                .banner(Banner.builder().format(request.adUnits.get(0).sizes).build())
                .build();
        BidRequest bidRequest = BidRequest.builder()
                .id(bidder.bidId)
                .app(request.app)
                .device(request.device)
                .imp(Collections.singletonList(imp))
                .build();

        Future<BidResponse> future = Future.future();
        try {
            URL url = new URL(endpoint);
            client.post(url.getPort(), url.getHost(), url.getFile(), clientResponseHandler(future))
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .setTimeout(request.timeoutMillis)
                    .exceptionHandler(throwable -> {
                        if (!future.isComplete())
                            future.complete(NO_BID_RESPONSE);
                    })
                    .end(Json.encode(bidRequest));
        } catch (MalformedURLException e) {
            future.complete(NO_BID_RESPONSE);
        }
        return future;
    }

    private static Handler<HttpClientResponse> clientResponseHandler(Future<BidResponse> future) {
        return response -> {
            if (response.statusCode() == 200)
                response
                        .bodyHandler(buffer -> future.complete(Json.decodeValue(buffer.toString(), BidResponse.class)))
                        .exceptionHandler(exception -> future.complete(NO_BID_RESPONSE));
            else
                future.complete(NO_BID_RESPONSE);
        };
    }
}
