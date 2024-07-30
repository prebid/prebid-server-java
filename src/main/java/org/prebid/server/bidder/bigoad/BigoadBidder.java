package org.prebid.server.bidder.bigoad;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bigoad.ExtImpBigoad;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class BigoadBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBigoad>> BIGOAD_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String SSP_ID_MACRO = "{{SspId}}";
    private static final String OPEN_RTB_VERSION = "2.5";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BigoadBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp());

        final Imp firstImp = modifiedImps.getFirst();
        final ExtImpBigoad extImpBigoad;
        try {
            extImpBigoad = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        modifiedImps.set(0, modifyImp(firstImp, extImpBigoad));
        return Result.withValue(BidderUtil.defaultRequest(
                updateBidRequest(request, modifiedImps),
                headers(),
                makeEndpointUrl(extImpBigoad),
                mapper));
    }

    private ExtImpBigoad parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BIGOAD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp %s: unable to unmarshal ext.bidder: %s"
                    .formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp, ExtImpBigoad extImpBigoad) {
        return imp.toBuilder().ext(mapper.mapper().valueToTree(extImpBigoad)).build();
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, List<Imp> imps) {
        return bidRequest.toBuilder().imp(imps).build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPEN_RTB_VERSION);
    }

    private String makeEndpointUrl(ExtImpBigoad extImpBigoadx) {
        final String safeSspId = HttpUtil.encodeUrl(StringUtils.trimToEmpty(extImpBigoadx.getSspId()));
        return endpointUrl.replace(SSP_ID_MACRO, safeSspId);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = parseBidResponse(httpCall.getResponse());
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse parseBidResponse(HttpResponse response) {
        try {
            return mapper.decodeValue(response.getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Bad server response: " + e.getMessage());
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = resolveBidType(bid.getMtype(), bid.getImpid());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType resolveBidType(Integer mType, String impId) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException("unrecognized bid type in response from bigoad " + impId);
        };
    }
}
