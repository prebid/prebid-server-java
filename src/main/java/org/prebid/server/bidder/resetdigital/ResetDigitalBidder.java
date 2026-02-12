package org.prebid.server.bidder.resetdigital;

import com.fasterxml.jackson.core.type.TypeReference;
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
        if (CollectionUtils.isEmpty(request.getImp()) || request.getImp().size() != 1) {
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

        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, uri, mapper));
    }

    private ExtImpResetDigital parseImpExt(Imp imp) {
        try {
            final ExtPrebid<?, ExtImpResetDigital> extPrebid = mapper.mapper()
                    .convertValue(imp.getExt(), EXT_TYPE_REFERENCE);

            if (extPrebid == null || extPrebid.getBidder() == null) {
                throw new PreBidException("imp.ext.bidder is required");
            }

            return extPrebid.getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing resetDigitalExt from imp.ext: " + e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpResetDigital extImp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (StringUtils.isBlank(imp.getTagid())) {
            impBuilder.tagid(extImp.getPlacementId());
        }

        return impBuilder.build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, httpCall.getRequest().getPayload());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.withValues(Collections.emptyList());
        }

        final String currency = StringUtils.isNotBlank(bidResponse.getCur())
                ? bidResponse.getCur()
                : DEFAULT_CURRENCY;

        return bidsFromResponse(bidResponse, bidRequest, currency);
    }

    private static Result<List<BidderBid>> bidsFromResponse(BidResponse bidResponse,
                                                            BidRequest bidRequest,
                                                            String currency) {
        final List<BidderBid> bids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid == null || seatBid.getBid() == null) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                if (!BidderUtil.isValidPrice(bid.getPrice())) {
                    errors.add(BidderError.badServerResponse(
                            "price %s <= 0 filtered out".formatted(bid.getPrice())));
                    continue;
                }

                try {
                    final BidType bidType = getBidType(bid, bidRequest);
                    bids.add(BidderBid.of(bid, bidType, currency));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }

        return Result.of(bids, errors);
    }

    private static BidType getBidType(Bid bid, BidRequest bidRequest) {
        final Integer mtype = bid.getMtype();
        if (mtype != null) {
            return switch (mtype) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 3 -> BidType.audio;
                case 4 -> BidType.xNative;
                default -> throw new PreBidException("Unsupported MType: " + mtype);
            };
        }

        final Imp imp = bidRequest.getImp().getFirst();
        if (!imp.getId().equals(bid.getImpid())) {
            throw new PreBidException("No matching impression found for ImpID: " + bid.getImpid());
        }

        return getMediaType(imp);
    }

    private static BidType getMediaType(Imp imp) {
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
