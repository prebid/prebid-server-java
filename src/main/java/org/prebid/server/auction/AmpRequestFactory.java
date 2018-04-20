package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AmpRequestFactory {

    private static final String TAG_ID_REQUEST_PARAM = "tag_id";
    private static final String DEBUG_REQUEST_PARAM = "debug";

    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;

    public AmpRequestFactory(StoredRequestProcessor storedRequestProcessor,
                             AuctionRequestFactory auctionRequestFactory) {
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
    }

    /**
     * Method determines {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    public Future<BidRequest> fromRequest(RoutingContext context) {
        final String tagId = context.request().getParam(TAG_ID_REQUEST_PARAM);

        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
        }

        return storedRequestProcessor.processAmpRequest(tagId)
                .map(bidRequest -> validateStoredBidRequest(tagId, bidRequest))
                .map(bidRequest -> fillExplicitParameters(bidRequest, context))
                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, context))
                .map(auctionRequestFactory::validateRequest);
    }

    private static BidRequest validateStoredBidRequest(String tagId, BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            throw new InvalidRequestException(
                    String.format("data for tag_id='%s' does not define the required imp array.", tagId));
        }

        final int impSize = imps.size();
        if (impSize > 1) {
            throw new InvalidRequestException(
                    String.format("data for tag_id '%s' includes %d imp elements. Only one is allowed",
                            tagId, impSize));
        }

        if (bidRequest.getExt() == null) {
            throw new InvalidRequestException("AMP requests require Ext to be set");
        }
        return bidRequest;
    }

    /**
     * Updates {@link BidRequest}.ext.prebid.targeting and {@link BidRequest}.ext.prebid.cache.bids with default values
     * if it was not included by user. Updates {@link Imp} security if required to ensure that amp always uses
     * https protocol. Sets {@link BidRequest}.test = 1 if it was passed in context.
     */
    private static BidRequest fillExplicitParameters(BidRequest bidRequest, RoutingContext context) {
        final List<Imp> imps = bidRequest.getImp();
        // Force HTTPS as AMP requires it, but pubs can forget to set it.
        final Imp imp = imps.get(0);
        final Integer secure = imp.getSecure();
        final boolean setSecure = secure == null || secure != 1;

        // AMP won't function unless ext.prebid.targeting and ext.prebid.cache.bids are defined.
        // If the user didn't include them, default those here.
        final ExtBidRequest requestExt;
        try {
            requestExt = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }

        final ExtRequestPrebid prebid = requestExt.getPrebid();

        final boolean setDefaultTargeting;
        final boolean setDefaultCache;

        if (prebid == null) {
            setDefaultTargeting = true;
            setDefaultCache = true;
        } else {
            final ExtRequestTargeting targeting = prebid.getTargeting();
            setDefaultTargeting = targeting == null
                    || targeting.getIncludewinners() == null
                    || targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull();
            final ExtRequestPrebidCache cache = prebid.getCache();
            setDefaultCache = cache == null || cache.getBids().isNull();
        }

        final String debugQueryParam = context.request().getParam(DEBUG_REQUEST_PARAM);
        final Integer test = bidRequest.getTest();
        final boolean setTestParam = !Objects.equals(test, 1) && Objects.equals(debugQueryParam, "1");

        return setDefaultTargeting || setDefaultCache || setSecure || setTestParam
                ? bidRequest.toBuilder()
                .ext(createExtWithDefaults(bidRequest, prebid, setDefaultTargeting, setDefaultCache))
                .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                .test(setTestParam ? Integer.valueOf(1) : test)
                .build()
                : bidRequest;
    }

    /**
     * Creates updated with default values bidrequest.ext {@link ObjectNode}
     */
    private static ObjectNode createExtWithDefaults(BidRequest bidRequest, ExtRequestPrebid prebid,
                                                    boolean setDefaultTargeting, boolean setDefaultCache) {
        final boolean isPrebidNull = prebid == null;

        return setDefaultTargeting || setDefaultCache
                ? Json.mapper.valueToTree(ExtBidRequest.of(
                ExtRequestPrebid.of(
                        isPrebidNull ? Collections.emptyMap() : prebid.getAliases(),
                        isPrebidNull ? Collections.emptyMap() : prebid.getBidadjustmentfactors(),
                        setDefaultTargeting || isPrebidNull
                                ? createTargetingWithDefaults(prebid) : prebid.getTargeting(),
                        isPrebidNull ? null : prebid.getStoredrequest(),
                        setDefaultCache
                                ? ExtRequestPrebidCache.of(Json.mapper.createObjectNode())
                                : isPrebidNull ? null : prebid.getCache())))
                : bidRequest.getExt();
    }

    /**
     * Creates updated with default values bidrequest.ext.targeting {@link ExtRequestTargeting} if at least one of it's
     * child properties is missed or entire targeting does not exist.
     */
    private static ExtRequestTargeting createTargetingWithDefaults(ExtRequestPrebid prebid) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final boolean isTargetingNull = targeting == null;

        final boolean includeWinners = isTargetingNull || targeting.getIncludewinners() == null
                ? true : targeting.getIncludewinners();

        final JsonNode priceGranularity = isTargetingNull ? null : targeting.getPricegranularity();
        final boolean isPriceGranularityNull = priceGranularity == null || priceGranularity.isNull();

        final JsonNode outgoingPriceGranularityNode = isPriceGranularityNull
                ? Json.mapper.valueToTree(PriceGranularity.DEFAULT.getBuckets())
                : priceGranularity;

        return ExtRequestTargeting.of(outgoingPriceGranularityNode, includeWinners);
    }
}
