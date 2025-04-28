package org.prebid.server.bidder.mobkoi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.mobkoi.ExtImpMobkoi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class MobkoiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMobkoi>> MOBKOI_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MobkoiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {

        final Imp firstImp = bidRequest.getImp().stream().findFirst().orElse(null);
        if (firstImp == null) {
            return Result.withError(BidderError.badInput("No impression provided"));
        }

        final ExtImpMobkoi extImpMobkoi;
        try {
            extImpMobkoi = parseExtImp(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        if (extImpMobkoi.getPlacementId() == null) {
            return Result.withError(BidderError.badInput("placementId should not be null"));
        }

        final String selectedEndpointUrl = Optional.ofNullable(extImpMobkoi.getAdServerBaseUrl())
                .filter(this::validCustomEndpointUrl)
                .orElse(endpointUrl);

        final List<Imp> modifiedImps = List.of(modifyImp(firstImp, extImpMobkoi));

        return Result.withValue(BidderUtil.defaultRequest(
                        modifyBidRequest(bidRequest, modifiedImps),
                        selectedEndpointUrl,
                        mapper));
    }

    private ExtImpMobkoi parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MOBKOI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "Invalid imp.ext for impression id %s. Error Information: %s"
                            .formatted(imp.getId(), e.getMessage()));
        }
    }

    private boolean validCustomEndpointUrl(String customUrl) {
        try {
            HttpUtil.validateUrl(Objects.requireNonNull(customUrl));
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private Imp modifyImp(Imp imp, ExtImpMobkoi extImpMobkoi) {
        final ObjectNode ext = mapper.mapper().createObjectNode();
        ext.set("bidder", mapper.mapper().valueToTree(extImpMobkoi));
        return imp.toBuilder().ext(ext).build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps) {
        return bidRequest.toBuilder().imp(imps).build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid()
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing mediaType for bid: " + bid.getId());
        }

        // markupType = 1 is a banner
        if (markupType != 1) {
            throw new PreBidException(
                    "Unsupported bid mediaType: %s for impression: %s"
                            .formatted(markupType, bid.getImpid()));
        }

        return BidType.banner;
    }
}
