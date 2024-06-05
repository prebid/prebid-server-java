package org.prebid.server.bidder.amt;

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
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.amt.ExtImpAmt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AmtBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAmt>> AMT_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AmtBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpAmt extImp = parseImpExt(imp);
                final Imp updatedImp = processImp(imp, extImp);
                validImps.add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions for AMT"));
            return Result.withErrors(errors);
        }

        final BidRequest outgoingBidRequest = bidRequest.toBuilder().imp(validImps).build();

        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(outgoingBidRequest, endpointUrl, mapper)),
                errors);
    }

    private ExtImpAmt parseImpExt(Imp imp) {
        final ExtImpAmt extImpAmt;
        try {
            extImpAmt = mapper.mapper().convertValue(imp.getExt(), AMT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Wrong AMT bidder ext in imp: " + imp.getId(), e);
        }

        return extImpAmt;
    }

    private Imp processImp(Imp imp, ExtImpAmt extImpAmt) {
        return imp.toBuilder()
                .bidfloor(resolveBidFloor(imp, extImpAmt))
                .ext(makeImpExt(extImpAmt))
                .build();
    }

    private ObjectNode makeImpExt(ExtImpAmt extImpAmt) {
        validateBidCeiling(extImpAmt);
        final ExtImpAmt processedExtBuilder = ExtImpAmt.of(extImpAmt.getPlacementId(), extImpAmt.getBidFloor(),
                extImpAmt.getBidCeiling());
        return mapper.mapper().valueToTree(ExtPrebid.of(null, processedExtBuilder));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(bid -> isInBidPriceRange(bid, bidRequest.getImp()))
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private boolean isInBidPriceRange(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                final ExtImpAmt extImp = parseImpExt(imp);
                final BigDecimal bidPrice = bid.getPrice();
                return compareBidPrice(bidPrice, imp.getBidfloor(), extImp.getBidCeiling());
            }
        }
        return false;
    }

    private boolean compareBidPrice(BigDecimal bidPrice, BigDecimal bidFloor, BigDecimal bidCeiling) {
        if (bidFloor != null && bidCeiling != null) {
            return bidPrice.compareTo(bidFloor) >= 0 && bidPrice.compareTo(bidCeiling) <= 0;
        } else if (bidFloor != null) {
            return bidPrice.compareTo(bidFloor) >= 0;
        } else if (bidCeiling != null) {
            return bidPrice.compareTo(bidCeiling) <= 0;
        }

        return true;
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        return BidType.banner;
    }

    private static void validateBidCeiling(ExtImpAmt extImpAmt) {
        final BigDecimal bidFloor = extImpAmt.getBidFloor();
        final BigDecimal bidCeiling = extImpAmt.getBidCeiling();
        if (BidderUtil.isValidPrice(bidFloor)
                && BidderUtil.isValidPrice(bidCeiling) && bidFloor.compareTo(bidCeiling) >= 0) {
            throw new PreBidException("Bid ceiling should be greater than bid floor in bidRequest.imp.ext for Amt");
        }
    }

    private static BigDecimal resolveBidFloor(Imp imp, ExtImpAmt extImpAmt) {
        final Price bidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(bidFloorPrice) ? bidFloorPrice.getValue() : extImpAmt.getBidFloor();
    }
}
