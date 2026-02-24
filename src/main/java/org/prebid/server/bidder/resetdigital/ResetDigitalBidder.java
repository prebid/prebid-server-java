package org.prebid.server.bidder.resetdigital;

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
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.resetdigital.ExtImpResetDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ResetDigitalBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final TypeReference<ExtPrebid<?, ExtImpResetDigital>> EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ResetDigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (request.getImp().size() != 1) {
            return Result.withError(BidderError.badInput(
                    "ResetDigital adapter supports only one impression per request"));
        }

        final Imp imp = request.getImp().getFirst();
        final ExtImpResetDigital extImp;
        try {
            extImp = parseImpExt(imp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final Imp modifiedImp = modifyImp(imp, extImp);
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(Collections.singletonList(modifiedImp))
                .build();

        final String uri = endpointUrl + "?pid=" + HttpUtil.encodeUrl(extImp.getPlacementId());
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, headers, uri, mapper));
    }

    private ExtImpResetDigital parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing resetDigitalExt from imp.ext: " + e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpResetDigital extImp) {
        return StringUtils.isBlank(imp.getTagid())
                ? imp.toBuilder().tagid(extImp.getPlacementId()).build()
                : imp;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload(), errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               BidRequest bidRequest,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Imp imp = bidRequest.getImp().getFirst();
        final String currency = StringUtils.isNotBlank(bidResponse.getCur())
                ? bidResponse.getCur()
                : bidRequest.getCur().stream().findFirst().orElse(DEFAULT_CURRENCY);

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid == null || CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }

            for (Bid bid : seatBid.getBid()) {
                try {
                    bidderBids.add(makeBidderBid(bid, seatBid.getSeat(), currency, imp));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }

        return bidderBids;
    }

    private static BidderBid makeBidderBid(Bid bid, String seat, String currency, Imp imp) {
        if (!BidderUtil.isValidPrice(bid.getPrice())) {
            throw new PreBidException("price %s <= 0 filtered out".formatted(bid.getPrice()));
        }

        final BidType bidType = Optional.ofNullable(getBidType(bid))
                .orElseGet(() -> getBidType(bid, imp));

        return StringUtils.isNotBlank(seat)
                ? BidderBid.of(bid, bidType, seat, currency)
                : BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(Bid bid) {
        final Integer mtype = bid.getMtype();
        return switch (mtype) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null -> null;
            default -> throw new PreBidException("Unsupported MType: " + mtype);
        };
    }

    private static BidType getBidType(Bid bid, Imp imp) {
        if (!imp.getId().equals(bid.getImpid())) {
            throw new PreBidException("No matching impression found for ImpID: " + bid.getImpid());
        }

        if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getAudio() != null) {
            return BidType.audio;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        }
        return BidType.banner;
    }
}
