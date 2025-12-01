package org.prebid.server.bidder.goldbach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.goldbach.proto.ExtImpGoldbachBidRequest;
import org.prebid.server.bidder.goldbach.proto.ExtRequestGoldbach;
import org.prebid.server.bidder.goldbach.proto.GoldbachImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.goldbach.ExtImpGoldbach;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class GoldbachBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpGoldbach>> GOLDBACH_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GoldbachBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        final ExtRequestGoldbach extRequestGoldbach = parseRequestExt(request, errors);
        final Map<String, List<Imp>> publisherToImps = groupImpressionsByPublisherId(request.getImp(), errors);
        final List<HttpRequest<BidRequest>> httpRequests = publisherToImps.entrySet().stream()
                .map(publisherIdAndImps ->
                        makeHttpRequestForPublisher(request,
                                extRequestGoldbach,
                                publisherIdAndImps.getKey(),
                                publisherIdAndImps.getValue(),
                                errors))
                .filter(Objects::nonNull)
                .toList();

        return Result.of(httpRequests, errors);
    }

    private ExtRequestGoldbach parseRequestExt(BidRequest bidRequest, List<BidderError> errors) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(extRequest -> extRequest.getProperty("goldbach"))
                .map(extRequestGoldbachRaw -> parseRequestExtGoldbach(extRequestGoldbachRaw, errors))
                .orElse(null);
    }

    private ExtRequestGoldbach parseRequestExtGoldbach(JsonNode extRequestGoldbachRaw, List<BidderError> errors) {
        try {
            return mapper.mapper().treeToValue(extRequestGoldbachRaw, ExtRequestGoldbach.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            errors.add(BidderError.badInput("Failed to deserialize Goldbach bid request extension: " + e.getMessage()));
            return null;
        }
    }

    private Map<String, List<Imp>> groupImpressionsByPublisherId(List<Imp> impressions, List<BidderError> errors) {
        final Map<String, List<Imp>> publisherToImps = new HashMap<>();
        for (final Imp imp : impressions) {
            try {
                final ExtImpGoldbach extImp = parseImpExt(imp);
                final Imp updatedImp = modifyImp(imp, extImp);

                publisherToImps.computeIfAbsent(extImp.getPublisherId(), k -> new ArrayList<>())
                        .add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (publisherToImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions found"));
        }
        return publisherToImps;
    }

    private ExtImpGoldbach parseImpExt(Imp imp) {
        final ExtPrebid<?, ExtImpGoldbach> extImp;
        try {
            extImp = mapper.mapper().convertValue(imp.getExt(), GOLDBACH_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Goldbach imp extension: " + e.getMessage());
        }

        if (extImp == null) {
            throw new PreBidException("imp.ext is missing");
        }
        final ExtImpGoldbach extImpGoldbach = extImp.getBidder();

        if (extImpGoldbach == null) {
            throw new PreBidException("imp.ext.bidder is missing");
        }

        return extImpGoldbach;
    }

    private Imp modifyImp(Imp imp, ExtImpGoldbach extImp) {
        final GoldbachImpExt goldbachImpExt = GoldbachImpExt.of(
                ExtImpGoldbachBidRequest.of(extImp.getSlotId(), extImp.getCustomTargeting()));
        return imp.toBuilder()
                .ext(mapper.mapper().valueToTree(goldbachImpExt))
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequestForPublisher(
            BidRequest bidRequest,
            ExtRequestGoldbach extRequestGoldbach,
            String publisherId,
            List<Imp> imps,
            List<BidderError> errors) {
        try {
            final BidRequest modifiedBidRequest = modifyBidRequest(bidRequest, extRequestGoldbach, publisherId, imps);
            return BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidRequest modifyBidRequest(
            BidRequest bidRequest,
            ExtRequestGoldbach extRequestGoldbach,
            String publisherId,
            List<Imp> imps) {
        final ExtRequest modifiedExtRequest = modifyExtRequest(bidRequest.getExt(), extRequestGoldbach, publisherId);
        return bidRequest.toBuilder()
                .id("%s_%s".formatted(bidRequest.getId(), publisherId))
                .imp(imps)
                .ext(modifiedExtRequest)
                .build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest,
                                        ExtRequestGoldbach extRequestGoldbach,
                                        String publisherId) {
        final ExtRequestGoldbach modifiedExtRequestGoldbach = ExtRequestGoldbach.builder()
                .publisherId(publisherId)
                .mockResponse(extRequestGoldbach != null ? extRequestGoldbach.getMockResponse() : null)
                .build();

        final ExtRequest modifiedExtRequest = ExtRequest.empty();
        Optional.ofNullable(extRequest)
                .map(ExtRequest::getProperties)
                .ifPresent(modifiedExtRequest::addProperties);
        modifiedExtRequest.addProperty("goldbach", serializeExtRequestGoldbach(modifiedExtRequestGoldbach));
        return modifiedExtRequest;
    }

    private JsonNode serializeExtRequestGoldbach(ExtRequestGoldbach extRequestGoldbach) {
        try {
            return mapper.mapper().valueToTree(extRequestGoldbach);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to serialize Goldbach bid request extension: " + e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse().getStatusCode() != HttpResponseStatus.CREATED.code()) {
            return Result.withError(
                    BidderError.badServerResponse(
                            "unexpected status code: %d. Run with request.debug = 1 for more info".formatted(
                                    httpCall.getResponse().getStatusCode())));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        return bidsFromResponse(bidResponse);
    }

    private BidResponse decodeBodyToBidResponse(BidderCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Failed to parse response as BidResponse: " + e.getMessage());
        }
    }

    private Result<List<BidderBid>> bidsFromResponse(BidResponse bidResponse) {
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = Stream
                .ofNullable(bidResponse)
                .map(BidResponse::getSeatbid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBid(bid, bidResponse, errors))
                .filter(Objects::nonNull)
                .toList();

        if (bidderBids.isEmpty()) {
            errors.add(BidderError.badServerResponse("No valid bids found in response"));
        }

        return Result.of(bidderBids, errors);
    }

    private BidderBid makeBid(Bid bid, BidResponse bidResponse, List<BidderError> errors) {
        try {
            return BidderBid.of(
                    bid,
                    getBidType(bid),
                    bidResponse.getCur());
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(this::parseBidExt)
                .map(ExtPrebid::getPrebid)
                .map(ExtBidPrebid::getType)
                .orElseThrow(() -> new PreBidException("No media type for bid " + bid.getId()));
    }

    private ExtPrebid<ExtBidPrebid, ObjectNode> parseBidExt(ObjectNode bidExt) {
        try {
            return mapper.mapper().convertValue(bidExt, EXT_PREBID_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize ext for bid: " + e.getMessage());
        }
    }
}
