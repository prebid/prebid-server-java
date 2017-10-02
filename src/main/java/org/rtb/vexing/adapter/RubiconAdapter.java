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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;

public class RubiconAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    private final String endpoint;
    private final String usersyncUrl;
    private final String xapiUsername;
    private final String xapiPassword;

    private final URL endpointUrl;
    private final String xapiCredentialsBase64;

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword) {
        this.endpoint = endpoint;
        this.usersyncUrl = usersyncUrl;
        this.xapiUsername = xapiUsername;
        this.xapiPassword = xapiPassword;

        endpointUrl = parseUrl(endpoint);
        xapiCredentialsBase64 = Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    @Override
    public Future<BidResponse> clientBid(HttpClient client, Bidder bidder, PreBidRequest request) {
        final Imp imp = Imp.builder()
                .id(bidder.bidId)
                .banner(Banner.builder().format(request.adUnits.get(0).sizes).build())
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .id(bidder.bidId)
                .app(request.app)
                .device(request.device)
                .imp(Collections.singletonList(imp))
                .build();

        // FIXME: remove
        logger.debug("Bid request is {0}", Json.encodePrettily(bidRequest));

        final Future<BidResponse> future = Future.future();
        client.post(getPort(endpointUrl), endpointUrl.getHost(), endpointUrl.getFile(), bidResponseHandler(future))
                .putHeader(HttpHeaders.AUTHORIZATION, "Basic " + xapiCredentialsBase64)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .setTimeout(request.timeoutMillis)
                .exceptionHandler(throwable -> {
                    logger.error("Error occurred while sending bid request to an exchange", throwable);
                    if (!future.isComplete())
                        future.complete(NO_BID_RESPONSE);
                })
                .end(Json.encode(bidRequest));
        return future;
    }

    private static int getPort(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }

    private static Handler<HttpClientResponse> bidResponseHandler(Future<BidResponse> future) {
        return response -> {
            if (response.statusCode() == 200) {
                response
                        .bodyHandler(buffer -> {
                            final String body = buffer.toString();
                            // FIXME: remove
                            logger.debug("Bid response body: {0}",
                                    Json.encodePrettily(Json.decodeValue(body, BidResponse.class)));
                            future.complete(Json.decodeValue(body, BidResponse.class));
                        })
                        .exceptionHandler(exception -> {
                            logger.error("Error occurred during bid response handling", exception);
                            future.complete(NO_BID_RESPONSE);
                        });
            } else {
                response.bodyHandler(buffer ->
                        logger.error("Bid response code is {0}, body: {1}",
                                response.statusCode(), buffer.toString()));
                future.complete(NO_BID_RESPONSE);
            }
        };
    }
}
