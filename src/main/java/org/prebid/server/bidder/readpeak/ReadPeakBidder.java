package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ReadPeakBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpReadPeak>> READPEAK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ReadPeakBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpReadPeak extImpReadPeak;
            try {
                extImpReadPeak = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpReadPeak);
                httpRequests.add(makeHttpRequest(request, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            return Result.withError(BidderError.badInput("found no valid impressions"));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpReadPeak parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), READPEAK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize ReadPeak extension: " + e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpReadPeak extImpReadPeak) {
        final Price bidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());

        return imp.toBuilder()
                .bidfloor(BidderUtil.isValidPrice(bidFloorPrice)
                        ? bidFloorPrice.getValue()
                        : extImpReadPeak.getBidFloor())
                .tagid(extImpReadPeak.getTagId())
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bids = extractBids(bidResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(bid -> {
                    final Bid resolvedBid = resolveMacros(bid);
                    final BidType bidType = getBidType(bid);
                    final Bid updatedBid = addBidMeta(resolvedBid);
                    return BidderBid.of(updatedBid, bidType, bidResponse.getCur());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .build();
    }

    private BidType getBidType(Bid bid) {
        final JsonNode typeNode = Optional.ofNullable(bid.getExt())
                .map(extNode -> extNode.get("prebid"))
                .map(extPrebidNode -> extPrebidNode.get("type"))
                .orElse(null);

        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to parse bid.ext.prebid.type for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        if (bidType == null) {
            throw new PreBidException("bid.ext.prebid.type is not present for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        return switch (bidType) {
            case banner, xNative -> bidType;
            default -> throw new PreBidException("Unsupported BidType: "
                    + bidType.getName() + " for bid.id: '" + bid.getId() + "'");
        };
    }

    private Bid addBidMeta(Bid bid) {
        final ObjectNode bidExt = bid.getExt() != null
                ? bid.getExt().deepCopy()
                : mapper.mapper().createObjectNode();

        final ObjectNode prebidNode = bidExt.putObject("prebid");
        final ObjectNode metaNode = prebidNode.putObject("meta");
        metaNode.set("advertiserDomains", mapper.mapper().valueToTree(bid.getAdomain()));
        return bid.toBuilder()
                .ext(bidExt)
                .build();
    }
}
