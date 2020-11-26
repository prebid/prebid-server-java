package org.prebid.server.bidder.ttx;

import com.fasterxml.jackson.core.type.TypeReference;
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
        final HttpRequest<BidRequest> httpRequest = createRequest(request, errors);

        if (httpRequest == null) {
            return Result.withErrors(errors);
        }

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, List<BidderError> errors) {
        final BidRequest.BidRequestBuilder requestToUpdate = request.toBuilder();
        modifyRequest(request, requestToUpdate, errors);
        final BidRequest modifiedRequest = requestToUpdate.build();
        if (modifiedRequest.getImp().get(0).getBanner() == null
                && modifiedRequest.getImp().get(0).getVideo() == null) {
            errors.add(BidderError.badInput("At least one of [banner, video] "
                    + "formats must be defined in Imp. None found"));
            return null;
        }

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .body(mapper.encode(modifiedRequest))
                .build();
    }

    private void modifyRequest(BidRequest request,
                               BidRequest.BidRequestBuilder modifiedRequest,
                               List<BidderError> errors) {
        final Imp firstImp = request.getImp().get(0);
        final Imp.ImpBuilder modifiedFirstImp = firstImp.toBuilder();
        try {
            processModify(request, modifiedRequest, firstImp, modifiedFirstImp);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> imps = new ArrayList<>(request.getImp());
        imps.set(0, modifiedFirstImp.build());
        modifiedRequest.imp(imps);
    }

    private void processModify(BidRequest request,
                               BidRequest.BidRequestBuilder modifiedRequest,
                               Imp firstImp,
                               Imp.ImpBuilder modifiedFirstImp) {
        final ExtImpTtx extImpTtx;
        extImpTtx = parseImpExt(firstImp);
        final String zoneId = extImpTtx.getZoneId();
        final TtxImpExt ttxImpExt = TtxImpExt.of(
                TtxImpExtTtx.of(extImpTtx.getProductId(), StringUtils.isNotBlank(zoneId) ? zoneId : null));

        modifiedFirstImp.ext(mapper.mapper().valueToTree(ttxImpExt)).build();
        final Site site = request.getSite();
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        modifiedRequest.site(siteBuilder.id(extImpTtx.getSiteId()).build());
        final Video video = firstImp.getVideo();
        if (firstImp.getVideo() != null) {
            modifiedFirstImp.video(modifyVideo(video, extImpTtx.getProductId()));
        }
    }

    private ExtImpTtx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TTX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Video modifyVideo(Video video, String prod) {
        if (video.getW() == null
                || video.getW() == 0
                || video.getH() == null
                || video.getH() == 0
                || CollectionUtils.isEmpty(video.getProtocols())
                || CollectionUtils.isEmpty(video.getMimes())
                || CollectionUtils.isEmpty(video.getPlaybackmethod())
        ) {
            throw new PreBidException("One or more invalid or missing video field(s) "
                    + "w, h, protocols, mimes, playbackmethod");
        }
        final Video.VideoBuilder modifiedVideo = video.toBuilder();
        if (video.getPlacement() == null || video.getPlacement() == 0) {
            modifiedVideo.placement(2);
        }
        if (Objects.equals(prod, "instream")) {
            modifiedVideo.placement(1);
            if (video.getStartdelay() == null) {
                modifiedVideo.startdelay(0);
            }
        }
        return modifiedVideo.build();
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
            TtxBidExt ttxBidExt = mapper.mapper().convertValue(bid.getExt(), TtxBidExt.class);
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
