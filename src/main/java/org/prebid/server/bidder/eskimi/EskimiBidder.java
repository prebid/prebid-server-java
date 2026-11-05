package org.prebid.server.bidder.eskimi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.IntNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.eskimi.ExtImpEskimi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EskimiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpEskimi>> ESKIMI_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public EskimiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpEskimi firstExt;
        try {
            validateRequest(request);
            firstExt = parseImpExt(request.getImp().getFirst());
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

    private static void validateRequest(BidRequest request) {
        if (request.getSite() == null && request.getApp() == null) {
            throw new PreBidException("request must contain either site or app");
        }
    }

    private ExtImpEskimi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ESKIMI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext for imp %s: %s".formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp) {
        final ExtImpEskimi extImp = parseImpExt(imp);
        final List<Integer> bAttr = extImp.getBattr();
        final Price price = resolvePrice(imp, extImp);

        return imp.toBuilder()
                .banner(imp.getBanner() != null ? modifyBanner(imp.getBanner(), bAttr) : null)
                .video(imp.getVideo() != null ? modifyVideo(imp.getVideo(), bAttr) : null)
                .bidfloor(price.getValue())
                .bidfloorcur(price.getCurrency())
                .secure(ObjectUtils.getIfNull(imp.getSecure(), 1))
                .build();
    }

    private static Banner modifyBanner(Banner banner, List<Integer> bAttr) {
        return CollectionUtils.isNotEmpty(bAttr) && CollectionUtils.isEmpty(banner.getBattr())
                ? banner.toBuilder().battr(bAttr).build()
                : banner;
    }

    private static Video modifyVideo(Video video, List<Integer> bAttr) {
        return CollectionUtils.isNotEmpty(bAttr) && CollectionUtils.isEmpty(video.getBattr())
                ? video.toBuilder().battr(bAttr).build()
                : video;
    }

    private static Price resolvePrice(Imp imp, ExtImpEskimi extImp) {
        final BigDecimal originalPrice = imp.getBidfloor();
        final String originalCurrency = imp.getBidfloorcur();

        final BigDecimal newPrice = extImp.getBidFloor();

        return !BidderUtil.isValidPrice(originalPrice) && BidderUtil.isValidPrice(newPrice)
                ? Price.of(StringUtils.defaultIfBlank(extImp.getBidFloorCur(), originalCurrency), newPrice)
                : Price.of(originalCurrency, originalPrice);
    }

    private static BidRequest modifyBidRequest(BidRequest request, List<Imp> validImps, ExtImpEskimi ext) {
        final Site modifiedSite = request.getSite() != null
                ? modifySite(request.getSite(), ext.getPlacementId())
                : null;
        final App modifiedApp = modifiedSite == null
                ? modifyApp(request.getApp(), ext.getPlacementId())
                : request.getApp();

        return request.toBuilder()
                .imp(validImps)
                .site(modifiedSite)
                .app(modifiedApp)
                .bcat(CollectionUtils.isEmpty(request.getBcat()) ? ext.getBcat() : request.getBcat())
                .badv(CollectionUtils.isEmpty(request.getBadv()) ? ext.getBadv() : request.getBadv())
                .bapp(CollectionUtils.isEmpty(request.getBapp()) ? ext.getBapp() : request.getBapp())
                .build();
    }

    private static Site modifySite(Site site, Integer placementId) {
        return site.toBuilder()
                .ext(addPlacementId(ObjectUtils.getIfNull(site.getExt(), () -> ExtSite.of(null, null)), placementId))
                .build();
    }

    private static App modifyApp(App app, Integer placementId) {
        return app.toBuilder()
                .ext(addPlacementId(ObjectUtils.getIfNull(app.getExt(), () -> ExtApp.of(null, null)), placementId))
                .build();
    }

    private static <T extends FlexibleExtension> T addPlacementId(T ext, Integer placementId) {
        ext.addProperty("placementId", IntNode.valueOf(placementId));
        return ext;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid createBidderBid(Bid bid,
                                             List<Imp> imps,
                                             String currency,
                                             List<BidderError> errors) {

        try {
            return BidderBid.of(bid, getBidType(bid, imps), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        return Optional.ofNullable(bidTypeFromMtype(bid))
                .orElseGet(() -> bidTypeFromImp(bid.getImpid(), imps));
    }

    private static BidType bidTypeFromMtype(Bid bid) {
        return switch (bid.getMtype()) {
            case null -> null;
            case 0 -> null;
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException(
                    "unsupported bid.mtype %d for impression %s (banner and video only)"
                            .formatted(bid.getMtype(), bid.getImpid()));
        };
    }

    private static BidType bidTypeFromImp(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (!imp.getId().equals(impId)) {
                continue;
            }

            final boolean hasBanner = imp.getBanner() != null;
            final boolean hasVideo = imp.getVideo() != null;

            if (hasBanner && hasVideo) {
                throw new PreBidException(
                        "bid for multi-format imp %s requires bid.mtype to disambiguate".formatted(impId));
            }
            if (hasBanner) {
                return BidType.banner;
            }
            if (hasVideo) {
                return BidType.video;
            }

            throw new PreBidException(
                    "unsupported media type for impression %s (banner and video only)".formatted(impId));
        }

        throw new PreBidException(
                "unable to resolve media type for impression %s".formatted(impId));
    }
}
