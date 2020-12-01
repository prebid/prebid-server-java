package org.prebid.server.bidder.ttx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.ttx.proto.TtxImpExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExtTtx;
import org.prebid.server.bidder.ttx.response.TtxBidExt;
import org.prebid.server.bidder.ttx.response.TtxBidExtTtx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ttx.ExtImpTtx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 33across {@link Bidder} implementation.
 */
public class TtxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTtx>> TTX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTtx>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TtxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        if (request.getImp().get(0).getBanner() == null
                && request.getImp().get(0).getVideo() == null) {
            errors.add(BidderError.badInput("At least one of [banner, video] "
                    + "formats must be defined in Imp. None found"));
            return Result.withErrors(errors);
        }
        final HttpRequest<BidRequest> httpRequest = createRequest(request, errors);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, List<BidderError> errors) {
        final Imp firstImp = request.getImp().get(0);
        Site updatedSite = null;
        Imp updatedFirstImp = null;
        try {
            final ExtImpTtx extImpTtx = parseImpExt(firstImp);
            updatedSite = updateSite(request.getSite(), extImpTtx.getSiteId());
            updatedFirstImp = updateFirstImp(firstImp,
                    extImpTtx.getProductId(), extImpTtx.getZoneId(), errors);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }
        final BidRequest modifiedRequest = updateRequest(request, updatedSite, updatedFirstImp);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .body(mapper.encode(modifiedRequest))
                .build();
    }

    private ExtImpTtx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TTX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp updateFirstImp(Imp firstImp, String productId,
                               String zoneId, List<BidderError> errors) {
        final Imp.ImpBuilder modifiedFirstImp = firstImp.toBuilder();
        modifiedFirstImp.ext(createImpExt(productId, zoneId)).build();

        final Video video = firstImp.getVideo();
        try {
            if (video != null) {
                modifiedFirstImp.video(updateVideo(video, productId));
            }
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        return modifiedFirstImp.build();
    }

    private List<Imp> replaceFirstImp(List<Imp> imps, Imp firstImp) {
        final List<Imp> updatedImpList = new ArrayList<>(imps);
        updatedImpList.set(0, firstImp);
        return updatedImpList;
    }

    private ObjectNode createImpExt(String productId, String zoneId) {
        final TtxImpExt ttxImpExt = TtxImpExt.of(TtxImpExtTtx.of(productId, StringUtils.stripToNull(zoneId)));
        return mapper.mapper().valueToTree(ttxImpExt);
    }

    private Video updateVideo(Video video, String productId) {
        if (isZeroOrNullInteger(video.getW())
                || isZeroOrNullInteger(video.getH())
                || CollectionUtils.isEmpty(video.getProtocols())
                || CollectionUtils.isEmpty(video.getMimes())
                || CollectionUtils.isEmpty(video.getPlaybackmethod())
        ) {
            throw new PreBidException("One or more invalid or missing video field(s) "
                    + "w, h, protocols, mimes, playbackmethod");
        }

        return modifyVideo(video, productId);
    }

    private Video modifyVideo(Video video, String productId) {
        final Integer resolvedPlacement = resolvePlacement(video.getPlacement(), productId);
        final Integer resolvedStartDelay = resolveStartDelay(productId);

        return resolvedPlacement != null || resolvedStartDelay != null
                ? video.toBuilder()
                .startdelay(resolvedStartDelay != null ? resolvedStartDelay : video.getStartdelay())
                .placement(resolvedPlacement != null ? resolvedPlacement : video.getPlacement()).build()
                : video;
    }

    private Integer resolvePlacement(Integer videoPlacement, String productId) {
        return Objects.equals(productId, "instream")
                ? 1 : isZeroOrNullInteger(videoPlacement)
                ? 2 : null;
    }

    private Integer resolveStartDelay(String productId) {
        return Objects.equals(productId, "instream")
                ? 0 : null;
    }

    private Site updateSite(Site site, String siteId) {
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        return siteBuilder.id(siteId).build();
    }

    private static boolean isZeroOrNullInteger(Integer integer) {
        return integer == null || integer == 0;
    }

    private BidRequest updateRequest(BidRequest request, Site site, Imp firstImp) {
        if (site == null && firstImp == null) {
            return request;
        }
        final List<Imp> requestImps = request.getImp();
        return request.toBuilder()
                .site(site != null ? site : request.getSite())
                .imp(firstImp != null ? replaceFirstImp(requestImps, firstImp) : requestImps)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(bid, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidderBid createBidderBid(Bid bid, String currency) {
        BidType bidType;
        try {
            final TtxBidExt ttxBidExt = mapper.mapper().convertValue(bid.getExt(), TtxBidExt.class);
            bidType = ttxBidExt != null ? getBidType(ttxBidExt.getTtx()) : BidType.banner;
        } catch (IllegalArgumentException e) {
            bidType = BidType.banner;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(TtxBidExtTtx bidExt) {
        if (bidExt != null && Objects.equals(bidExt.getMediaType(), "video")) {
            return BidType.video;
        }

        return BidType.banner;
    }
}
