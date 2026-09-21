package org.prebid.server.bidder.eskimi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.IntNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.eskimi.ExtImpEskimi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.springframework.util.CollectionUtils;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class EskimiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpEskimi>> ESKIMI_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public EskimiBidder(@NotBlank String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpEskimi firstExt;
        try {
            firstExt = parseImpExt(request.getImp().getFirst());
            validateRequest(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> validImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = modifyBidRequest(request, validImps, firstExt);

        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper)), errors);
    }

    private ExtImpEskimi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ESKIMI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext for imp %s: %s".formatted(imp.getId(), e.getMessage()));
        }
    }

    private void validateRequest(BidRequest request) {
        if (Objects.isNull(request.getSite()) && Objects.isNull(request.getApp())) {
            throw new PreBidException("request must contain either site or app");
        }
    }

    private Imp modifyImp(Imp imp) {
        final ExtImpEskimi extImp = parseImpExt(imp);
        final List<Integer> battr = extImp.getBattr();
        final Imp.ImpBuilder builder = imp.toBuilder();

        if (!CollectionUtils.isEmpty(battr)) {
            if (Objects.nonNull(imp.getBanner()) && CollectionUtils.isEmpty(imp.getBanner().getBattr())) {
                builder.banner(imp.getBanner().toBuilder().battr(battr).build());
            }
            if (Objects.nonNull(imp.getVideo()) && CollectionUtils.isEmpty(imp.getVideo().getBattr())) {
                builder.video(imp.getVideo().toBuilder().battr(battr).build());
            }
        }
        if (!BidderUtil.isValidPrice(imp.getBidfloor()) && BidderUtil.isValidPrice(extImp.getBidFloor())) {
            builder.bidfloor(extImp.getBidFloor());
            if (StringUtils.isNotBlank(extImp.getBidFloorCur())) {
                builder.bidfloorcur(extImp.getBidFloorCur());
            }
        }
        if (Objects.isNull(imp.getSecure())) {
            builder.secure(1);
        }
        return builder.build();
    }

    private BidRequest modifyBidRequest(BidRequest request, List<Imp> validImps, ExtImpEskimi ext) {
        final BidRequest.BidRequestBuilder builder = request.toBuilder();

        if (Objects.nonNull(request.getSite())) {
            final ExtSite siteExt = Objects.nonNull(request.getSite().getExt())
                    ? request.getSite().getExt()
                    : ExtSite.of(null, null);

            siteExt.addProperty("placementId", IntNode.valueOf(ext.getPlacementId()));
            builder.site(request.getSite().toBuilder().ext(siteExt).build());
        } else {
            final ExtApp appExt = Objects.nonNull(request.getApp().getExt())
                    ? request.getApp().getExt()
                    : ExtApp.of(null, null);

            appExt.addProperty("placementId", IntNode.valueOf(ext.getPlacementId()));
            builder.app(request.getApp().toBuilder().ext(appExt).build());
        }

        return builder
                .imp(validImps)
                .bcat(CollectionUtils.isEmpty(request.getBcat()) ? ext.getBcat() : request.getBcat())
                .badv(CollectionUtils.isEmpty(request.getBadv()) ? ext.getBadv() : request.getBadv())
                .bapp(CollectionUtils.isEmpty(request.getBapp()) ? ext.getBapp() : request.getBapp())
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                                    BidResponse bidResponse,
                                                    List<BidderError> errors) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, bidRequest, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid createBidderBid(Bid bid,
                                             BidRequest bidRequest,
                                             String currency,
                                             List<BidderError> errors) {

        try {
            final BidType bidType = getBidType(bid, bidRequest.getImp());
            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        if (!BidderUtil.isNullOrZero(bid.getMtype())) {
            return switch (bid.getMtype()) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                default ->
                        throw new PreBidException("unsupported bid.mtype %d for impression %s (banner and video only)"
                                .formatted(bid.getMtype(), bid.getImpid()));
            };
        }

        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                final boolean hasBanner = Objects.nonNull(imp.getBanner());
                final boolean hasVideo = Objects.nonNull(imp.getVideo());

                if (hasBanner && hasVideo) {
                    throw new PreBidException(
                            "bid for multi-format imp %s requires bid.mtype to disambiguate".formatted(bid.getImpid()));
                }
                if (hasBanner) {
                    return BidType.banner;
                }
                if (hasVideo) {
                    return BidType.video;
                }

                throw new PreBidException(String.format(
                        "unsupported media type for impression %s (banner and video only)",
                        bid.getImpid()));
            }
        }

        throw new PreBidException(
                String.format("unable to resolve media type for impression %s", bid.getImpid()));
    }
}
