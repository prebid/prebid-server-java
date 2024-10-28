package org.prebid.server.bidder.tradplus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.tradplus.ExtImpTradPlus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TradPlusBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTradPlus>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    public static final String X_OPENRTB_VERSION = "2.5";

    private static final String ZONE_ID = "{{ZoneID}}";
    private static final String ACCOUNT_ID = "{{AccountID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TradPlusBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            final ExtImpTradPlus extImpTradPlus = parseImpExt(bidRequest.getImp().getFirst().getExt());
            validateImpExt(extImpTradPlus);
            final HttpRequest<BidRequest> httpRequest;
            httpRequest = makeHttpRequest(extImpTradPlus, bidRequest.getImp(), bidRequest);
            return Result.withValue(httpRequest);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private ExtImpTradPlus parseImpExt(ObjectNode extNode) {
        final ExtImpTradPlus extImpTradPlus;
        try {
            extImpTradPlus = mapper.mapper().convertValue(extNode, EXT_TYPE_REFERENCE).getBidder();
            return extImpTradPlus;
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private void validateImpExt(ExtImpTradPlus extImpTradPlus) {
        if (StringUtils.isBlank(extImpTradPlus.getAccountId())) {
            throw new PreBidException("Invalid/Missing AccountID");
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(ExtImpTradPlus extImpTradPlus, List<Imp> imps,
                                                    BidRequest bidRequest) {
        final String uri;
        uri = endpointUrl.replace(ZONE_ID, extImpTradPlus.getZoneId()).replace(ACCOUNT_ID,
                extImpTradPlus.getAccountId());

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(removeImpsExt(imps)).build();

        return BidderUtil.defaultRequest(outgoingRequest, makeHeaders(), uri, mapper);
    }

    private MultiMap makeHeaders() {
        return HttpUtil.headers().set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
    }

    private static List<Imp> removeImpsExt(List<Imp> imps) {
        return imps.stream().map(imp -> imp.toBuilder().ext(null).build()).toList();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid()) ? Collections
                .emptyList() : bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse.getSeatbid().stream().filter(Objects::nonNull).map(SeatBid::getBid)
                .filter(Objects::nonNull).flatMap(Collection::stream).map(bid -> BidderBid
                        .of(bid, getBidType(bid.getImpid(), imps), bidResponse.getCur())).toList();
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                return BidType.banner;
            }
        }
        throw new PreBidException(
                "Invalid bid imp ID #%s does not match any imp IDs from the original bid request".formatted(impId));
    }

}
