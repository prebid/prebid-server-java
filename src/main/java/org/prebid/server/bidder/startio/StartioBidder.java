package org.prebid.server.bidder.startio;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StartioBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final JsonPointer BID_TYPE_POINTER = JsonPointer.valueOf("/prebid/type");
    private static final String BID_CURRENCY = "USD";

    public StartioBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {

        if (hasAppOrSiteId(bidRequest)) {
            return Result.withError(BidderError.badInput(
                    "Bidder requires either app.id or site.id to be specified."));
        }

        if (isSupportedCurrency(bidRequest.getCur())) {
            return Result.withError(BidderError.badInput("Unsupported currency, bidder only accepts USD."));
        }

        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = bidRequest.getImp();
        for (int i = 0; i < imps.size(); i++) {
            Imp imp = imps.get(i);
            if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
                errors.add(BidderError.badInput(
                        "imp[%d]: Unsupported media type, bidder does not support audio.".formatted(i)));
            } else {
                if (imp.getAudio() != null) {
                    imp = imp.toBuilder().audio(null).build();
                }
                requests.add(BidderUtil.defaultRequest(
                        bidRequest.toBuilder().imp(Collections.singletonList(imp)).build(),
                        endpointUrl, mapper));
            }
        }

        return Result.of(requests, errors);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse response;

        try {
            response = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        return Result.of(extractBids(response, errors), errors);
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> constructBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid constructBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType type = getBidType(bid);
        if (type != null) {
            return BidderBid.of(bid, type, currency);
        }

        errors.add(BidderError.badServerResponse(
                "Failed to parse bid media type for impression %s.".formatted(bid.getImpid())));
        return null;
    }

    private static BidType getBidType(Bid bid) {
        final JsonNode ext = bid.getExt();
        if (ext == null) {
            return null;
        }

        final JsonNode bidType = ext.at(BID_TYPE_POINTER);
        if (bidType == null || !bidType.isTextual()) {
            return null;
        }

        return switch (bidType.textValue()) {
            case "banner" -> BidType.banner;
            case "video" -> BidType.video;
            case "native" -> BidType.xNative;
            default -> null;
        };
    }

    private static boolean isSupportedCurrency(List<String> currencies) {
        return currencies != null && !currencies.isEmpty() && !currencies.contains(BID_CURRENCY);
    }

    private static boolean hasAppOrSiteId(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        return (app == null || app.getId() == null || app.getId().isEmpty())
                && (site == null || site.getId() == null || site.getId().isEmpty());
    }

}
