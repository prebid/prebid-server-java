package org.prebid.server.bidder.somoaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.somoaudience.ExtImpSomoaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SomoaudienceBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSomoaudience>> SOMOAUDIENCE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSomoaudience>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;

    public SomoaudienceBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        headers = BidderUtil.headers();
    }

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Result<Map<String, List<Imp>>> placementsToImpsResult = mapPlacementToImp(request.getImp());
        return createHttpRequests(placementsToImpsResult.getValue(), placementsToImpsResult.getErrors(), request);
    }

    /**
     * Validates and creates {@link Map} where placement hash is used as key and {@link List} of {@link Imp} as value.
     */
    private Result<Map<String, List<Imp>>> mapPlacementToImp(List<Imp> imps) {

        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> placementToImps = new HashMap<>();
        for (final Imp imp : imps) {
            final String placement;
            try {
                placement = getPlacementHash(imp);
            } catch (PreBidException ex) {
                errors.add(BidderError.badInput(ex.getMessage()));
                continue;
            }
            final List<Imp> placementImps = placementToImps.get(placement);

            if (placementImps == null) {
                placementToImps.put(placement, new ArrayList<>(Collections.singleton(imp)));
            } else {
                placementImps.add(imp);
            }
        }
        return Result.of(placementToImps, errors);
    }

    /**
     * Validates {@link Imp}s. Throws {@link PreBidException} in case of {@link Imp} is invalid.
     */
    private String getPlacementHash(Imp imp) {
        final String impId = imp.getId();
        if (imp.getAudio() != null) {
            throw new PreBidException(String.format("ignoring imp id=%s, Somoaudience doesn't support Audio", impId));
        }

        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException(String.format("ignoring imp id=%s, extImpBidder is empty", impId));
        }

        final ExtImpSomoaudience extImpSomoaudience;

        try {
            extImpSomoaudience = Json.mapper.<ExtPrebid<?, ExtImpSomoaudience>>convertValue(imp.getExt(),
                    SOMOAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding extImpBidder, err: %s", imp.getId(), e.getMessage()));
        }

        return extImpSomoaudience.getPlacementHash();
    }

    /**
     * Creates {@link HttpRequest}s. One for each source id. Adds source id as url parameter
     */
    private Result<List<HttpRequest<BidRequest>>> createHttpRequests(Map<String, List<Imp>> placementsToImps,
                                                                     List<BidderError> errors, BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Map.Entry<String, List<Imp>> placementIdToImps : placementsToImps.entrySet()) {
            final String url = String.format("%s?s=%s", endpointUrl, placementIdToImps.getKey());
            final BidRequest bidRequest = request.toBuilder().imp(placementIdToImps.getValue()).build();
            final String bidRequestBody;
            try {
                bidRequestBody = Json.encode(bidRequest);
            } catch (EncodeException e) {
                errors.add(BidderError.badInput(String.format("error while encoding bidRequest, err: %s",
                        e.getMessage())));
                return Result.of(Collections.emptyList(), errors);
            }
            httpRequests.add(HttpRequest.of(HttpMethod.POST, url, bidRequestBody, headers, bidRequest));
        }
        return Result.of(httpRequests, errors);
    }


    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Result.of(Collections.emptyList(), Collections.emptyList())
                : Result.of(createBiddersBid(bidResponse, imps), Collections.emptyList());
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private static List<BidderBid> createBiddersBid(BidResponse bidResponse, List<Imp> imps) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidderType(imps, bid.getImpid()), null))
                .collect(Collectors.toList());
    }

    /**
     * Defines {@link BidType} depends on {@link Imp} with the same impId
     */
    private static BidType getBidderType(List<Imp> imps, String impId) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findAny()
                .map(SomoaudienceBidder::bidTypeFromImp)
                .orElse(BidType.banner);
    }

    /**
     * Returns {@link BidType} depends on {@link Imp}s banner, video or native types.
     */
    private static BidType bidTypeFromImp(Imp imp) {
        BidType bidType = BidType.banner;
        if (imp.getBanner() == null) {
            if (imp.getVideo() != null) {
                bidType = BidType.video;
            } else if (imp.getXNative() != null) {
                bidType = BidType.xNative;
            }
        }
        return bidType;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
