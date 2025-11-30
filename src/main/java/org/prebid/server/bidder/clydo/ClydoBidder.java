package org.prebid.server.bidder.clydo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.clydo.ExtImpClydo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClydoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpClydo>> CLYDO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String REGION_MACRO = "{{Region}}";
    private static final String PARTNER_ID_MACRO = "{{PartnerId}}";
    private static final String DEFAULT_REGION = "us";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ClydoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpClydo extImpClydo = parseExtImp(imp);
                final HttpRequest<BidRequest> httpRequest = makeHttpRequest(request, imp, extImpClydo);
                httpRequests.add(httpRequest);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            return Result.withError(BidderError.badInput("found no valid impressions"));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpClydo parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CLYDO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Cannot deserialize ExtImpClydo: " + e.getMessage());
        }
    }

    private static String resolveUrl(String endpoint, ExtImpClydo extImp) {
        return endpoint
                .replace(REGION_MACRO, getRegionInfo(extImp))
                .replace(PARTNER_ID_MACRO, extImp.getPartnerId());
    }

    private static String getRegionInfo(ExtImpClydo extImp) {
        final String region = extImp.getRegion();
        if (region == null) {
            return DEFAULT_REGION;
        }

        return switch (region) {
            case "us", "usw", "eu", "apac" -> region;
            default -> DEFAULT_REGION;
        };
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp, ExtImpClydo extImpClydo) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, resolveUrl(endpointUrl, extImpClydo), mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bidderBids = extractBids(bidRequest, bidResponse);
            return Result.of(bidderBids, Collections.emptyList());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null || bidResponse.getSeatbid().isEmpty()) {
            return Collections.emptyList();
        }

        final Map<String, BidType> impIdToBidTypeMap = buildImpIdToBidTypeMap(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, resolveBidType(bid, impIdToBidTypeMap), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static Map<String, BidType> buildImpIdToBidTypeMap(BidRequest bidRequest) {
        if (bidRequest == null || bidRequest.getImp() == null || bidRequest.getImp().isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, BidType> impIdToBidTypeMap = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();
            if (impIdToBidTypeMap.containsKey(impId)) {
                throw new PreBidException("Duplicate impression ID found");
            }

            final BidType bidType = determineBidType(imp);
            if (bidType == null) {
                throw new PreBidException("Failed to get media type");
            }

            impIdToBidTypeMap.put(impId, bidType);
        }

        return impIdToBidTypeMap;
    }

    private static BidType determineBidType(Imp imp) {
        if (imp.getAudio() != null) {
            return BidType.audio;
        } else if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else if (imp.getBanner() != null) {
            return BidType.banner;
        }

        return null;
    }

    private static BidType resolveBidType(Bid bid, Map<String, BidType> impIdToBidTypeMap) {
        if (bid.getImpid() == null) {
            throw new PreBidException("Missing imp id for bid.id: '%s'".formatted(bid.getId()));
        }

        return impIdToBidTypeMap.getOrDefault(bid.getImpid(), BidType.banner);
    }
}
