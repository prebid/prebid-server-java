package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;

import java.util.Objects;

public class AmpRequestFactory {

    private static final String TAG_ID_REQUEST_PARAM = "tag_id";

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
                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, context))
                .map(auctionRequestFactory::validateRequest);
    }

    private static BidRequest validateStoredBidRequest(String tagId, BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            throw new InvalidRequestException(
                    String.format("AMP tag_id '%s' does not include an Imp object. One id required", tagId));
        }

        if (bidRequest.getImp().size() > 1) {
            throw new InvalidRequestException(
                    String.format("AMP tag_id '%s' includes multiple Imp objects. We must have only one", tagId));
        }

        if (bidRequest.getExt() == null) {
            throw new InvalidRequestException("AMP requests require Ext to be set");
        }

        final ExtBidRequest requestExt;
        try {
            requestExt = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }

        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        if (prebid == null || prebid.getTargeting() == null) {
            throw new InvalidRequestException("request.ext.prebid.targeting is required for AMP requests");
        }

        final ExtRequestPrebidCache cache = prebid.getCache();
        if (cache == null || cache.getBids().isNull()) {
            throw new InvalidRequestException("request.ext.prebid.cache.bids must be set to {} for AMP requests");
        }

        return bidRequest;
    }
}
