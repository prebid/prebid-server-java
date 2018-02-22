package org.prebid.server.cache;

import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.cache.model.BidCacheResult;
import org.prebid.server.cache.model.request.BannerValue;
import org.prebid.server.cache.model.request.BidCacheRequest;
import org.prebid.server.cache.model.request.PutObject;
import org.prebid.server.cache.model.response.BidCacheResponse;
import org.prebid.server.cache.model.response.CacheObject;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.model.MediaType;
import org.prebid.server.model.response.Bid;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Client stores values in Prebid Cache. For more info, see https://github.com/prebid/prebid-cache
 */
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private final HttpClient httpClient;
    private final String endpointUrl;
    private final String cachedAssetUrlTemplate;

    public CacheService(HttpClient httpClient, String endpointUrl, String cachedAssetUrlTemplate) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.cachedAssetUrlTemplate = Objects.requireNonNull(cachedAssetUrlTemplate);
    }

    /**
     * Makes cache for bids (legacy).
     * <p>
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<BidCacheResult>> cacheBids(List<Bid> bids, GlobalTimeout timeout) {
        Objects.requireNonNull(bids);
        Objects.requireNonNull(timeout);

        if (bids.isEmpty()) {
            return Future.succeededFuture(Collections.emptyList());
        }

        final String body = Json.encode(toRequest(bids));
        return makeRequest(body, bids.size(), timeout)
                .map(this::toResponse);
    }

    /**
     * Makes cache for OpenRTB bids.
     * <p>
     * Stores JSON values for the given {@link com.iab.openrtb.response.Bid}s in the cache.
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<String>> cacheBidsOpenrtb(List<com.iab.openrtb.response.Bid> bids, GlobalTimeout timeout) {
        Objects.requireNonNull(bids);
        Objects.requireNonNull(timeout);

        if (bids.isEmpty()) {
            return Future.succeededFuture(Collections.emptyList());
        }

        final String body = Json.encode(toRequestOpenrtb(bids));
        return makeRequest(body, bids.size(), timeout)
                .map(this::toResponseOpenrtb);
    }

    /**
     * Creates bid cache request for the given {@link Bid}s.
     */
    private static BidCacheRequest toRequest(List<Bid> bids) {
        final List<PutObject> putObjects = bids.stream()
                .map(CacheService::toPutObject)
                .collect(Collectors.toList());

        return BidCacheRequest.of(putObjects);
    }

    /**
     * Makes put object from {@link Bid}. Used for legacy auction request.
     */
    private static PutObject toPutObject(Bid bid) {
        if (MediaType.video.equals(bid.getMediaType())) {
            return PutObject.of("xml", new TextNode(bid.getAdm()));
        } else {
            return PutObject.of("json", Json.mapper.valueToTree(
                    BannerValue.of(bid.getAdm(), bid.getNurl(), bid.getWidth(), bid.getHeight())));
        }
    }

    /**
     * Creates bid cache request for the given {@link com.iab.openrtb.response.Bid}s.
     */
    private static BidCacheRequest toRequestOpenrtb(List<com.iab.openrtb.response.Bid> bids) {
        final List<PutObject> putObjects = bids.stream()
                .map(bid -> PutObject.of("json", Json.mapper.valueToTree(bid)))
                .collect(Collectors.toList());

        return BidCacheRequest.of(putObjects);
    }

    /**
     * Asks external prebid cache service to store the given value.
     */
    private Future<BidCacheResponse> makeRequest(String body, int bidCount, GlobalTimeout timeout) {
        final Future<BidCacheResponse> future = Future.future();

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            handleException(new TimeoutException(), future);
            return future;
        }

        httpClient.postAbs(endpointUrl, response -> handleResponse(response, bidCount, future))
                .exceptionHandler(exception -> handleException(exception, future))
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .setTimeout(remainingTimeout)
                .end(body);
        return future;
    }

    /**
     * Adds body handler and exception handler to {@link HttpClientResponse}.
     */
    private static void handleResponse(HttpClientResponse response, int bidCount, Future<BidCacheResponse> future) {
        response
                .bodyHandler(
                        buffer -> handleResponseAndBody(response.statusCode(), buffer.toString(), bidCount, future))
                .exceptionHandler(exception -> handleException(exception, future));
    }

    /**
     * Analyzes response status/body and completes input {@link Future}
     * with obtained result from prebid cache service or fails it in case of errors.
     */
    private static void handleResponseAndBody(int statusCode, String body, int bidCount,
                                              Future<BidCacheResponse> future) {
        if (statusCode != 200) {
            logger.warn("Cache service response code is {0}, body: {1}", statusCode, body);
            future.fail(new PreBidException(String.format("HTTP status code %d, body: %s", statusCode, body)));
            return;
        }

        final BidCacheResponse bidCacheResponse;
        try {
            bidCacheResponse = Json.decodeValue(body, BidCacheResponse.class);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid cache response: {0}", e, body);
            future.fail(e);
            return;
        }

        final List<CacheObject> responses = bidCacheResponse.getResponses();
        if (responses == null || responses.size() != bidCount) {
            future.fail(new PreBidException("Put response length didn't match"));
            return;
        }

        future.complete(bidCacheResponse);
    }

    /**
     * Completes input {@link Future} with the given exception.
     */
    private static void handleException(Throwable exception, Future<BidCacheResponse> future) {
        logger.warn("Error occurred while sending request to cache service", exception);
        future.fail(exception);
    }

    /**
     * Creates prebid cache service response for legacy auction request.
     */
    private List<BidCacheResult> toResponse(BidCacheResponse bidCacheResponse) {
        return bidCacheResponse.getResponses().stream()
                .map(cacheObject -> BidCacheResult.of(cacheObject.getUuid(), getCachedAssetURL(cacheObject.getUuid())))
                .collect(Collectors.toList());
    }

    /**
     * Creates prebid cache service response for OpenRTB auction request.
     */
    private List<String> toResponseOpenrtb(BidCacheResponse bidCacheResponse) {
        return bidCacheResponse.getResponses().stream()
                .map(CacheObject::getUuid)
                .collect(Collectors.toList());
    }

    /**
     * Composes cached asset URL for the given UUID cache value.
     */
    public String getCachedAssetURL(String uuid) {
        Objects.requireNonNull(uuid);
        return cachedAssetUrlTemplate.replaceFirst("%PBS_CACHE_UUID%", uuid);
    }

    /**
     * Composes prebid cache service url against the given schema and host.
     */
    public static String getCacheEndpointUrl(String cacheSchema, String cacheHost) {
        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, "/cache").toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cache endpoint for prebid cache service", e);
        }
    }

    /**
     * Composes cached asset url template against the given query, schema and host.
     */
    public static String getCachedAssetUrlTemplate(String cacheQuery, String cacheSchema, String cacheHost) {
        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, "/cache?" + cacheQuery).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cached asset url template for prebid cache service", e);
        }
    }

    /**
     * Returns prebid cache service url or throws {@link MalformedURLException} if error occurs.
     */
    private static URL getCacheBaseUrl(String cacheSchema, String cacheHost) throws MalformedURLException {
        return new URL(cacheSchema + "://" + cacheHost);
    }
}
