package org.prebid.server.bidder.gumgum;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgum;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gumgum {@link Bidder} implementation.
 */
public class GumgumBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpGumgum>> GUMGUM_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpGumgum>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GumgumBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final BidRequest outgoingRequest;
        try {
            outgoingRequest = createBidRequest(bidRequest, errors);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(mapper.encode(outgoingRequest))
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private BidRequest createBidRequest(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> modifiedImps = new ArrayList<>();
        String trackingId = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpGumgum impExt = parseImpExt(imp);
                if (imp.getBanner() != null) {
                    modifiedImps.add(modifyImp(imp));
                    trackingId = impExt.getZone();
                } else {
                    final Video video = imp.getVideo();
                    if (video != null) {
                        validateVideoParams(video);
                        modifiedImps.add(imp);
                        trackingId = impExt.getZone();
                    }
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            throw new PreBidException("No valid impressions");
        }

        final Site modifiedSite = modifySite(bidRequest.getSite(), trackingId);

        return bidRequest.toBuilder()
                .imp(modifiedImps)
                .site(modifiedSite)
                .build();
    }

    private ExtImpGumgum parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), GUMGUM_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp) {
        final Banner resolvedBanner = resolveBanner(imp.getBanner());
        if (resolvedBanner != null) {
            return imp.toBuilder()
                    .banner(resolvedBanner)
                    .build();
        }
        return imp;
    }

    private static Banner resolveBanner(Banner banner) {
        final List<Format> format = banner.getFormat();
        if (banner.getH() == null && banner.getW() == null && CollectionUtils.isNotEmpty(format)) {
            final Format firstFormat = format.get(0);
            return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
        }
        return null;
    }

    private void validateVideoParams(Video video) {
        if (anyOfNull(
                video.getW(),
                video.getH(),
                video.getMinduration(),
                video.getMaxduration(),
                video.getPlacement(),
                video.getLinearity())) {
            throw new PreBidException("Invalid or missing video field(s)");
        }
    }

    private static boolean anyOfNull(Integer... numbers) {
        return Arrays.stream(ArrayUtils.nullToEmpty(numbers)).anyMatch(GumgumBidder::isNullOrZero);
    }

    private static boolean isNullOrZero(Integer number) {
        return number == null || number == 0;
    }

    private static Site modifySite(Site site, String trackingId) {
        return site != null ? site.toBuilder().id(ObjectUtils.defaultIfNull(trackingId, "")).build() : null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, bidRequest), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidRequest, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidderBid toBidderBid(Bid bid, BidRequest bidRequest, String currency) {
        final BidType bidType = getMediaType(bid.getImpid(), bidRequest.getImp());
        final Bid updatedBid = bidType == BidType.video
                ? bid.toBuilder().adm(resolveAdm(bid.getAdm(), bid.getPrice())).build()
                : bid;
        return BidderBid.of(updatedBid, bidType, currency);
    }

    private static BidType getMediaType(String impId, List<Imp> requestImps) {
        for (Imp imp : requestImps) {
            if (imp.getId().equals(impId)) {
                return imp.getBanner() != null ? BidType.banner : BidType.video;
            }
        }
        return BidType.video;
    }

    private static String resolveAdm(String bidAdm, BigDecimal price) {
        return StringUtils.isNotBlank(bidAdm) ? bidAdm.replace("${AUCTION_PRICE}", String.valueOf(price)) : bidAdm;
    }
}
