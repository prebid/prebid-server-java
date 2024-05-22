package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ReadPeakBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpReadPeak>> READPEAK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PUBLISHER_ID_MACRO = "{{PublisherId}}";
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

        // Create a deep copy of the incoming request
        BidRequest requestCopy;
        try {
            requestCopy = mapper.decodeValue(mapper.encodeToBytes(request), BidRequest.class);
        } catch (Exception e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        final List<Imp> imps = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            final ExtImpReadPeak extImpReadPeak;
            try {
                extImpReadPeak = parseImpExt(imp);
                if (extImpReadPeak != null) {
                    final Imp updatedImp = updateImp(imp, extImpReadPeak);
                    imps.add(updatedImp);
                }
            } catch (IllegalArgumentException e) {
                errors.add(BidderError.badInput("Invalid Imp ext: " + e.getMessage()));
            }
        }

        if (imps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions found"));
            return Result.withErrors(errors);
        }

        requestCopy = requestCopy.toBuilder().imp(imps).build();

        final String url = makeUrl(imps.get(0).getExt().get("bidder").get("publisherId").asText());
        httpRequests.add(BidderUtil.defaultRequest(requestCopy, url, mapper));
        return Result.of(httpRequests, errors);
    }

    private ExtImpReadPeak parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), READPEAK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize ReadPeak extension: " + e.getMessage());
        }
    }

    private Imp updateImp(Imp imp, ExtImpReadPeak extImpReadPeak) {
        final Price bidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());

        return imp.toBuilder()
                .bidfloor(BidderUtil.isValidPrice(bidFloorPrice)
                        ? bidFloorPrice.getValue()
                        : extImpReadPeak.getBidFloor())
                .tagid(extImpReadPeak.getTagId())
                .build();
    }

    private String makeUrl(String publisherId) {
        return endpointUrl.replace(PUBLISHER_ID_MACRO, StringUtils.defaultString(publisherId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse() == null || httpCall.getResponse().getBody() == null) {
            return Result.withError(BidderError.badServerResponse("Empty response body"));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, mapper, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, JacksonMapper mapper, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidResponse, mapper, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, JacksonMapper mapper, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
//                .map(bid -> {
//                    resolveMacros(bid);
//
//                    .map(bid -> BidderBid.of(resolveMacros(bid), getMediaType(bid.getImpid(), bidRequest.getImp()),
//                            bidResponse.getCur()));
//                    final ObjectNode ext = bid.getExt() != null ? bid.getExt().deepCopy()
//                            : mapper.mapper().createObjectNode();
//                    ext.set("prebid", mapper.mapper().convertValue(getBidMeta(bid), ObjectNode.class));
//                    bid = bid.toBuilder().ext(ext).build();
//                    return BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur());
//                })
                .map(bid -> BidderBid.of(resolveMacros(bid), resolveBidType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
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

    private static BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        throw new PreBidException("Failed to find banner/native impression \"%s\"".formatted(impId));
    }

    private static ExtBidPrebidMeta getBidMeta(Bid bid) {
        return ExtBidPrebidMeta.builder()
                .advertiserDomains(bid.getAdomain())
                .build();
    }
}
