package org.prebid.server.bidder.elementaltv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.elementaltv.model.ElementalTVResponseAdsExt;
import org.prebid.server.bidder.elementaltv.model.ElementalTVResponseExt;
import org.prebid.server.bidder.elementaltv.model.ElementalTVResponseVideoAdsExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.elementaltv.ExtImpElementalTV;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ElementalTVBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpElementalTV>> ELEMENTALTV_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public ElementalTVBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpElementalTV validExtImp = parseAndValidateImpExt(imp);
                final String updateRequestId = request.getId() + "-" + validExtImp.getAdunit();
                final BidRequest updateRequest = request.toBuilder().id(updateRequestId).build();
                final String url = resolveUrl(validExtImp);

                result.add(createSingleRequest(imp, updateRequest, url));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(result, errors);
    }

    private ExtImpElementalTV parseAndValidateImpExt(Imp imp) {
        final ExtImpElementalTV extImpElementalTV;
        try {
            extImpElementalTV = mapper.mapper().convertValue(imp.getExt(), ELEMENTALTV_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(extImpElementalTV.getAdunit())) {
            throw new PreBidException("adunit parameter is required for elementaltv bidder");
        }
        return extImpElementalTV;
    }

    private String resolveUrl(ExtImpElementalTV extImp) {
        try {
            return endpointTemplate
                    .replace("{{AdUnit}}", HttpUtil.encodeUrl(extImp.getAdunit()));
        } catch (Exception e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();
        final MultiMap headers = HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers)
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = decodeBodyToBidResponse(httpCall);
            final Map<String, BidType> impTypes = getImpTypes(bidRequest);
            final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                    .filter(Objects::nonNull)
                    .map(SeatBid::getBid)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .map(bid -> createBid(bid, impTypes, bidResponse.getCur()))
                    .toList();
            return Result.withValues(bidderBids);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private BidResponse decodeBodyToBidResponse(BidderCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("invalid body: " + e.getMessage());
        }
    }

    private Map<String, BidType> getImpTypes(BidRequest bidRequest) {
        final Map<String, BidType> impTypes = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();

            if (imp.getBanner() != null) {
                impTypes.put(impId, BidType.banner);
            } else if (imp.getVideo() != null) {
                impTypes.put(impId, BidType.video);
            } else if (imp.getAudio() != null) {
                impTypes.put(impId, BidType.audio);
            } else if (imp.getXNative() != null) {
                impTypes.put(impId, BidType.xNative);
            }
        }
        return impTypes;
    }

    private BidderBid createBid(Bid bid, Map<String, BidType> impTypes, String currency) {
        final String bidImpId = bid.getImpid();

        if (impTypes.get(bidImpId) == null) {
            throw new PreBidException("unknown impId: " + bidImpId);
        }
        if (impTypes.get(bidImpId) == BidType.video) {
            validateVideoBidExt(bid);
        }

        return BidderBid.of(bid, impTypes.get(bidImpId), currency);
    }

    private void validateVideoBidExt(Bid bid) {
        final ObjectNode extNode = bid.getExt();
        final ElementalTVResponseExt ext = extNode != null ? parseResponseExt(extNode) : null;
        final ElementalTVResponseAdsExt adsExt = ext != null ? ext.getAds() : null;
        final ElementalTVResponseVideoAdsExt videoAdsExt = adsExt != null ? adsExt.getVideo() : null;
        if (videoAdsExt == null) {
            throw new PreBidException("$.seatbid.bid.ext.ads.video required");
        }
    }

    private ElementalTVResponseExt parseResponseExt(ObjectNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, ElementalTVResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }
}
