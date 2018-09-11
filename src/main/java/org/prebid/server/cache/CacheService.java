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
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.cache.proto.request.BannerValue;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public String getEndpointUrl() {
        return endpointUrl;
    }

    /**
     * Makes cache for bids (legacy).
     * <p>
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<BidCacheResult>> cacheBids(List<Bid> bids, Timeout timeout) {
        return doCache(bids, timeout, CacheService::createPutObject, this::createBidCacheResult);
    }

    /**
     * Makes cache for bids with video media type only (legacy).
     * <p>
     * The returned result will always have the same number of elements as the values argument.
     */
    public Future<List<BidCacheResult>> cacheBidsVideoOnly(List<Bid> bids, Timeout timeout) {
        return doCache(bids, timeout, CacheService::createPutObjectVideoOnly, this::createBidCacheResult);
    }

    /**
     * Makes cache for OpenRTB bids.
     * <p>
     * Stores JSON values for the given {@link com.iab.openrtb.response.Bid}s in the cache.
     * Stores XML cache objects for the given video {@link com.iab.openrtb.response.Bid}s in the cache.
     * <p>
     * The returned result will always have the number of elements equals to sum of sizes of bids and video bids.
     */
    public Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> cacheBidsOpenrtb(
            List<com.iab.openrtb.response.Bid> bids, List<com.iab.openrtb.response.Bid> videoBids, Integer bidsTtl,
            Integer videoBidsTtl, Timeout timeout) {
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> result;

        if (CollectionUtils.isEmpty(bids) && CollectionUtils.isEmpty(videoBids)) {
            result = Future.succeededFuture(Collections.emptyMap());
        } else {
            final List<PutObject> putObjects = Stream.concat(
                    bids.stream().map(bid -> createJsonPutObjectOpenrtb(bid, bidsTtl)),
                    videoBids.stream().map(bid -> createXmlPutObjectOpenrtb(bid, videoBidsTtl)))
                    .collect(Collectors.toList());

            result = makeRequest(BidCacheRequest.of(putObjects), putObjects.size(), timeout)
                    .map(bidCacheResponse -> toResponse(bidCacheResponse, CacheObject::getUuid))
                    .map(uuids -> toResultMap(bids, videoBids, uuids));
        }

        return result;
    }

    /**
     * Generic method to work with cache service.
     */
    private <T, R> Future<List<R>> doCache(List<T> bids, Timeout timeout,
                                           Function<T, PutObject> requestItemCreator,
                                           Function<CacheObject, R> responseItemCreator) {
        return makeRequest(toRequest(bids, requestItemCreator), bids.size(), timeout)
                .map(bidCacheResponse -> toResponse(bidCacheResponse, responseItemCreator));
    }

    /**
     * Asks external prebid cache service to store the given value.
     */
    private Future<BidCacheResponse> makeRequest(BidCacheRequest bidCacheRequest, int bidCount, Timeout timeout) {
        final Future<BidCacheResponse> future;
        if (bidCount == 0) {
            future = Future.succeededFuture(BidCacheResponse.of(Collections.emptyList()));
        } else {
            future = Future.future();

            final long remainingTimeout = timeout.remaining();
            if (remainingTimeout <= 0) {
                handleException(new TimeoutException(), future);
            } else {
                httpClient.postAbs(endpointUrl, response -> handleResponse(response, bidCount, future))
                        .exceptionHandler(exception -> handleException(exception, future))
                        .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                        .setTimeout(remainingTimeout)
                        .end(Json.encode(bidCacheRequest));
            }
        }
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
     * Makes put object from {@link Bid}. Used for legacy auction request.
     */
    private static PutObject createPutObject(Bid bid) {
        return MediaType.video.equals(bid.getMediaType()) ? videoPutObject(bid) : bannerPutObject(bid);
    }

    /**
     * Makes put object from {@link Bid} with video media type only. Used for legacy auction request.
     */
    private static PutObject createPutObjectVideoOnly(Bid bid) {
        return MediaType.video.equals(bid.getMediaType()) ? videoPutObject(bid) : null;
    }

    /**
     * Makes JSON type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private static PutObject createJsonPutObjectOpenrtb(com.iab.openrtb.response.Bid bid, Integer cacheTtl) {
        return PutObject.of("json", Json.mapper.valueToTree(bid), cacheTtl);
    }

    /**
     * Makes XML type {@link PutObject} from {@link com.iab.openrtb.response.Bid}. Used for OpenRTB auction request.
     */
    private static PutObject createXmlPutObjectOpenrtb(com.iab.openrtb.response.Bid bid, Integer cacheTtl) {
        return PutObject.of("xml", new TextNode(bid.getAdm()), cacheTtl);
    }

    /**
     * Creates bid cache request for the given bids.
     */
    private static <T> BidCacheRequest toRequest(List<T> bids, Function<T, PutObject> requestItemCreator) {
        return BidCacheRequest.of(bids.stream()
                .filter(Objects::nonNull)
                .map(requestItemCreator)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    /**
     * Transforms {@link CacheObject} into {@link BidCacheResult}. Used for legacy auction request.
     */
    private BidCacheResult createBidCacheResult(CacheObject cacheObject) {
        final String uuid = cacheObject.getUuid();
        return BidCacheResult.of(uuid, getCachedAssetURL(uuid));
    }

    /**
     * Creates prebid cache service response according to the creator.
     */
    private <T> List<T> toResponse(BidCacheResponse bidCacheResponse, Function<CacheObject, T> responseItemCreator) {
        return bidCacheResponse.getResponses().stream()
                .filter(Objects::nonNull)
                .map(responseItemCreator)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Creates a map with bids as a key and {@link CacheIdInfo} as a value from obtained UUIDs.
     */
    private static <T> Map<T, CacheIdInfo> toResultMap(List<T> bids, List<T> videoBids, List<String> uuids) {
        final Map<T, CacheIdInfo> result = new HashMap<>(uuids.size());

        // here we assume "videoBids" is a sublist of "bids"
        // so, no need for a separate loop on "videoBids" if "bids" is not empty
        if (!bids.isEmpty()) {
            for (int i = 0; i < bids.size(); i++) {
                final T bid = bids.get(i);

                // determine uuid for video bid
                final int indexOfVideoBid = videoBids.indexOf(bid);
                final String videoBidUuid = indexOfVideoBid != -1 ? uuids.get(bids.size() + indexOfVideoBid) : null;

                result.put(bid, CacheIdInfo.of(uuids.get(i), videoBidUuid));
            }
        } else {
            for (int i = 0; i < videoBids.size(); i++) {
                result.put(videoBids.get(i), CacheIdInfo.of(null, uuids.get(i)));
            }
        }

        return result;
    }

    /**
     * Composes cached asset URL for the given UUID cache value.
     */
    public String getCachedAssetURL(String uuid) {
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
    public static String getCachedAssetUrlTemplate(String cacheSchema, String cacheHost, String cacheQuery) {
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

    /**
     * Creates video {@link PutObject} from the given {@link Bid}. Used for legacy auction request.
     */
    private static PutObject videoPutObject(Bid bid) {
        return PutObject.of("xml", new TextNode(bid.getAdm()), null);
    }

    /**
     * Creates banner {@link PutObject} from the given {@link Bid}. Used for legacy auction request.
     */
    private static PutObject bannerPutObject(Bid bid) {
        return PutObject.of("json",
                Json.mapper.valueToTree(BannerValue.of(bid.getAdm(), bid.getNurl(), bid.getWidth(), bid.getHeight())),
                null);
    }
}
