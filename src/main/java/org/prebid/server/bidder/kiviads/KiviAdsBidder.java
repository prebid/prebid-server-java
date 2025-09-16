package org.prebid.server.bidder.kiviads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.kiviads.proto.KiviAdsImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kiviads.ExtImpKiviAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class KiviAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKiviAds>> KIVI_ADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public KiviAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> outgoingRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpKiviAds extImpKiviAds;
            try {
                extImpKiviAds = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            outgoingRequests.add(createSingleRequest(modifyImp(imp, extImpKiviAds), request));
        }

        return Result.withValues(outgoingRequests);
    }

    private ExtImpKiviAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), KIVI_ADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpKiviAds extImpKiviAds) {
        final KiviAdsImpExtBidder kiviAdsImpExtBidderWithType = getImpExtKiviAdsWithType(extImpKiviAds);
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        modifiedImpExtBidder.set("bidder", mapper.mapper().valueToTree(kiviAdsImpExtBidderWithType));

        return imp.toBuilder()
                .ext(modifiedImpExtBidder)
                .build();
    }

    private KiviAdsImpExtBidder getImpExtKiviAdsWithType(ExtImpKiviAds extImpKiviAds) {
        final KiviAdsImpExtBidder.KiviAdsImpExtBidderBuilder impExtKiviAds = KiviAdsImpExtBidder.builder();

        if (StringUtils.isNotEmpty(extImpKiviAds.getPlacementId())) {
            impExtKiviAds
                    .type("publisher")
                    .placementId(extImpKiviAds.getPlacementId());
        } else if (StringUtils.isNotEmpty(extImpKiviAds.getEndpointId())) {
            impExtKiviAds
                    .type("network")
                    .endpointId(extImpKiviAds.getEndpointId());
        }

        return impExtKiviAds.build();
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                break;
            }
        }

        throw new PreBidException("Failed to find impression '%s'".formatted(impId));
    }
}
