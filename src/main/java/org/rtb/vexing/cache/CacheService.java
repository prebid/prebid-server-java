package org.rtb.vexing.cache;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.rtb.vexing.cache.model.request.BidCacheRequest;
import org.rtb.vexing.cache.model.request.Put;
import org.rtb.vexing.cache.model.request.Value;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.model.response.Bid;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Interacts with prebid cache service
 */
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private static final Long HTTP_REQUEST_TIMEOUT = 1000L; // FIXME: request should be bounded by client timeout
    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private final HttpClient httpClient;
    private final URL cacheEndpointUrl;

    public static CacheService create(HttpClient httpClient, ApplicationConfig config) {
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(config);

        return new CacheService(httpClient, getCacheEndpointUrl(config));
    }

    private CacheService(HttpClient httpClient, URL cacheEndpointUrl) {
        this.httpClient = httpClient;
        this.cacheEndpointUrl = cacheEndpointUrl;
    }

    public Future<BidCacheResponse> saveBids(List<Bid> bids) {
        Objects.requireNonNull(bids);
        if (bids.isEmpty()) {
            return Future.succeededFuture(BidCacheResponse.builder().build());
        }

        final List<Put> puts = bids.stream()
                .map(bid -> Put.builder()
                        .type("json")
                        .value(Value.builder()
                                .adm(bid.adm)
                                .nurl(bid.nurl)
                                .width(bid.width)
                                .height(bid.height)
                                .build())
                        .build())
                .collect(Collectors.toList());

        final BidCacheRequest bidCacheRequest = BidCacheRequest.builder()
                .puts(puts)
                .build();

        // FIXME: remove
        logger.debug("Bid cache request: {0}", Json.encodePrettily(bidCacheRequest));

        final Future<BidCacheResponse> future = Future.future();
        httpClient.post(portFromUrl(cacheEndpointUrl), cacheEndpointUrl.getHost(), cacheEndpointUrl.getFile(),
                cacheResponse -> {
                    if (cacheResponse.statusCode() == 200) {
                        cacheResponse.bodyHandler(buffer -> {
                            final BidCacheResponse response = Json.decodeValue(buffer.toString(),
                                    BidCacheResponse.class);

                            // FIXME: remove
                            logger.debug("Bid cache response body raw: {0}", buffer.toString());
                            logger.debug("Bid cache response: {0}", Json.encodePrettily(response));

                            future.complete(response);
                        });
                    } else {
                        cacheResponse.bodyHandler(buffer ->
                                logger.error("Cache service response code is {0}, body: {1}",
                                        cacheResponse.statusCode(), buffer.toString()));
                        // FIXME: error handling
                        // future.fail("Prebid cache failed");
                        future.complete(null);
                    }
                })
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .setTimeout(HTTP_REQUEST_TIMEOUT)
                .exceptionHandler(throwable -> {
                    logger.error("Error occurred while sending request to cache service", throwable);
                    if (!future.isComplete()) {
                        // FIXME: error handling
                        // future.fail("Prebid cache failed");
                        future.complete(null);
                    }
                })
                .end(Json.encode(bidCacheRequest));
        return future;
    }

    private static URL getCacheEndpointUrl(ApplicationConfig config) {
        try {
            URL baseUrl = new URL(config.getString("prebid_cache_url"));
            return new URL(baseUrl, "/cache");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cache endpoint for prebid cache service", e);
        }
    }

    private static int portFromUrl(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }
}
