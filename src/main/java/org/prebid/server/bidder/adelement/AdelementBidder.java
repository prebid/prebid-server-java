package org.prebid.server.bidder.adelement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.adelement.ExtImpAdelement;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdelementBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private static final String SUPPLY_ID_MACRO = "{{SupplyId}}";
    private static final TypeReference<ExtPrebid<?, ExtImpAdelement>> ADELEMENT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    public AdelementBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Imp firstImp = bidRequest.getImp().getFirst();
        final ExtImpAdelement extImpAdelement;

        try {
            extImpAdelement = mapper.mapper().convertValue(firstImp.getExt(), ADELEMENT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput("Ext.bidder not provided"));
        }

        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveEndpoint(extImpAdelement.getSupplyId()))
                        .headers(HttpUtil.headers())
                        .body(mapper.encodeToBytes(bidRequest))
                        .impIds(BidderUtil.impIds(bidRequest))
                        .payload(bidRequest)
                        .build());
    }

    private String resolveEndpoint(String supplyId) {
        return endpointUrl.replace(SUPPLY_ID_MACRO, HttpUtil.encodeUrl(supplyId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
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
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getMtype()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(Integer mType) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;

            default -> throw new PreBidException("Unsupported mType " + mType);
        };
    }
}
