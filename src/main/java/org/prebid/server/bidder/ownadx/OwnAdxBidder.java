package org.prebid.server.bidder.ownadx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.ownadx.ExtImpOwnAdx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class OwnAdxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOwnAdx>> OWN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String X_OPEN_RTB_VERSION = "2.5";
    private static final String ACCOUNT_ID_MACROS_ENDPOINT = "{{AccountID}}";
    private static final String ZONE_ID_MACROS_ENDPOINT = "{{ZoneID}}";
    private static final String SOURCE_ID_MACROS_ENDPOINT = "{{SourceId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OwnAdxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpOwnAdx impOwnAdx = parseImpExt(imp);
                httpRequests.add(createHttpRequest(bidRequest, impOwnAdx));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpOwnAdx parseImpExt(Imp imp) {
        final ExtImpOwnAdx extImpOwnAdx;
        try {
            extImpOwnAdx = mapper.mapper().convertValue(imp.getExt(), OWN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
        return extImpOwnAdx;
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, ExtImpOwnAdx extImpOwnAdx) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(updateUrl(extImpOwnAdx))
                .headers(updateHeader())
                .body(mapper.encodeToBytes(bidRequest))
                .impIds(BidderUtil.impIds(bidRequest))
                .payload(bidRequest)
                .build();
    }

    private String updateUrl(ExtImpOwnAdx extImpOwnAdx) {
        return endpointUrl
                .replace(ACCOUNT_ID_MACROS_ENDPOINT, extImpOwnAdx.getSspId() != null ? extImpOwnAdx.getSspId() : "")
                .replace(ZONE_ID_MACROS_ENDPOINT, extImpOwnAdx.getSeatId() != null ? extImpOwnAdx.getSeatId() : "")
                .replace(SOURCE_ID_MACROS_ENDPOINT, extImpOwnAdx.getTokenId() != null ? extImpOwnAdx.getTokenId() : "");
    }

    private static MultiMap updateHeader() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPEN_RTB_VERSION);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
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
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException("Unable to fetch mediaType " + bid.getMtype());
        };
    }
}
