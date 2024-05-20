package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adtarget.proto.AdtargetImpExt;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;
import org.prebid.server.proto.openrtb.ext.request.alkimi.ExtImpAlkimi;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ReadPeakBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpReadPeak>> READPEAK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PUBLISHER_ID_MACRO = "{{PublisherId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ReadPeakBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

//    @Override
//    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
//        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
//        final List<BidderError> errors = new ArrayList<>();
//
//        for (Imp imp : request.getImp()) {
//            try {
//                final ExtImpReadPeak extImp = parseImpExt(imp);
//                httpRequests.add(makeHttpRequest(request, extImp));
//            } catch (PreBidException e) {
//                errors.add(BidderError.badInput(e.getMessage()));
//            }
//        }
//
//        return Result.of(httpRequests, errors);
//    }

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
                final Imp updatedImp = updateImp(imp, extImpReadPeak);
                imps.add(updatedImp);
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
        httpRequests.add(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(BidderUtil.headers())
                .payload(requestCopy)
                .build());

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

//    private String makeUrl(ExtImpReadPeak extImp) {
//        return endpointUrl
//                .replace(PUBLISHER_ID_MACRO, StringUtils.defaultString(extImp.getPublisherId()));
//    }

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
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }
}
