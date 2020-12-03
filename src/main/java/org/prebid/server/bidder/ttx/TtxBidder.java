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
        final Imp firstImp = request.getImp().get(0);
        final List<BidderError> errors = new ArrayList<>();
        Imp updatedFirstImp = null;
        ExtImpTtx extImpTtx = null;
        try {
            extImpTtx = parseImpExt(firstImp);
            updatedFirstImp = updatedImp(firstImp, extImpTtx.getProductId(), extImpTtx.getZoneId(), errors);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        if (updatedFirstImp != null && updatedFirstImp.getBanner() == null && updatedFirstImp.getVideo() == null) {
            return Result.withError(BidderError.badInput("At least one of [banner, video] "
                    + "formats must be defined in Imp. None found"));
        }

        final HttpRequest<BidRequest> httpRequest = createRequest(request, extImpTtx, updatedFirstImp);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpTtx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TTX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp updatedImp(Imp imp, String productId, String zoneId, List<BidderError> errors) {

        return imp.toBuilder()
                .video(updatedVideo(imp.getVideo(), productId, errors))
                .ext(createImpExt(productId, zoneId))
                .build();
    }

    private static Video updatedVideo(Video video, String productId, List<BidderError> errors) {
        if (video == null) {
            return null;
        }
        if (isZeroOrNullInteger(video.getW())
                || isZeroOrNullInteger(video.getH())
                || CollectionUtils.isEmpty(video.getProtocols())
                || CollectionUtils.isEmpty(video.getMimes())
                || CollectionUtils.isEmpty(video.getPlaybackmethod())) {
            errors.add(BidderError.badInput("One or more invalid or missing video field(s) "
                    + "w, h, protocols, mimes, playbackmethod"));
            return null;
        }
        final Integer videoPlacement = video.getPlacement();

        return video.toBuilder()
                .startdelay(resolveStartDelay(video.getStartdelay(), productId))
                .placement(resolvePlacement(videoPlacement, productId))
                .build();
    }

    private static boolean isZeroOrNullInteger(Integer integer) {
        return integer == null || integer == 0;
    }

    private static Integer resolveStartDelay(Integer startDelay, String productId) {
        return Objects.equals(productId, "instream") ? Integer.valueOf(0) : startDelay;
    }

    private static Integer resolvePlacement(Integer videoPlacement, String productId) {
        if (Objects.equals(productId, "instream")) {
            return 1;
        }
        if (isZeroOrNullInteger(videoPlacement)) {
            return 2;
        }
        return videoPlacement;
    }

    private ObjectNode createImpExt(String productId, String zoneId) {
        final TtxImpExt ttxImpExt = TtxImpExt.of(TtxImpExtTtx.of(productId, StringUtils.stripToNull(zoneId)));
        return mapper.mapper().valueToTree(ttxImpExt);
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, ExtImpTtx extImpTtx, Imp updatedFirstImp) {
        final Site updatedSite = extImpTtx != null ? updateSite(request.getSite(), extImpTtx.getSiteId()) : null;
        final BidRequest modifiedRequest = updateRequest(request, updatedSite, updatedFirstImp);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .body(mapper.encode(modifiedRequest))
                .build();
    }

    private static Site updateSite(Site site, String siteId) {
        return site == null ? null : site.toBuilder().id(siteId).build();
    }

    private static BidRequest updateRequest(BidRequest request, Site updatedSite, Imp updatedFirstImp) {
        if (updatedSite == null && updatedFirstImp == null) {
            return request;
        }
        final List<Imp> requestImps = request.getImp();
        return request.toBuilder()
                .site(updatedSite != null ? updatedSite : request.getSite())
                .imp(updatedFirstImp != null ? replaceFirstImp(requestImps, updatedFirstImp) : requestImps)
                .build();
    }

    private static List<Imp> replaceFirstImp(List<Imp> imps, Imp firstImp) {
        final List<Imp> updatedImpList = new ArrayList<>(imps);
        updatedImpList.set(0, firstImp);
        return updatedImpList;
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
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidType getBidType(Bid bid) {
        try {
            final TtxBidExt ttxBidExt = mapper.mapper().convertValue(bid.getExt(), TtxBidExt.class);
            return ttxBidExt != null ? getBidTypeByTtx(ttxBidExt.getTtx()) : BidType.banner;
        } catch (IllegalArgumentException e) {
            return BidType.banner;
        }
    }

    private static BidType getBidTypeByTtx(TtxBidExtTtx bidExt) {
        return bidExt != null && Objects.equals(bidExt.getMediaType(), "video")
                ? BidType.video
                : BidType.banner;
    }
}
