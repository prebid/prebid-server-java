package org.prebid.server.bidder.resetdigital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImp;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpMediaType;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpMediaTypes;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpZone;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalRequest;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalSite;
import org.prebid.server.bidder.resetdigital.response.ResetDigitalBid;
import org.prebid.server.bidder.resetdigital.response.ResetDigitalResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.request.resetdigital.ExtImpResetDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ResetDigitalBidder implements Bidder<ResetDigitalRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpResetDigital>> IMP_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ResetDigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<ResetDigitalRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<ResetDigitalRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp: request.getImp()) {
            try {
                final ExtImpResetDigital extImp = parseImpExt(imp);
                final ResetDigitalImp resetDigitalImp = makeImp(request, imp, extImp);
                requests.add(makeHttpRequest(request, resetDigitalImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpResetDigital parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), IMP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static ResetDigitalImp makeImp(BidRequest request, Imp imp, ExtImpResetDigital extImp) {
        return ResetDigitalImp.builder()
                .bidId(request.getId())
                .impId(imp.getId())
                .mediaTypes(resolveMediaTypes(imp))
                .zoneId(Optional.ofNullable(extImp.getPlacementId())
                        .map(ResetDigitalImpZone::of)
                        .orElse(null))
                .build();
    }

    private static ResetDigitalImpMediaTypes resolveMediaTypes(Imp imp) {
        return switch (mediaType(imp)) {
            case banner -> ResetDigitalImpMediaTypes.banner(makeBanner(imp.getBanner()));
            case video -> ResetDigitalImpMediaTypes.video(makeVideo(imp.getVideo()));
            case audio -> ResetDigitalImpMediaTypes.audio(makeAudio(imp.getAudio()));
            case null, default -> throw new PreBidException(
                    "Banner, video or audio must be present in the imp %s".formatted(imp.getId()));
        };
    }

    private static ImpMediaType mediaType(Imp imp) {
        if (imp.getBanner() != null) {
            return ImpMediaType.banner;
        }
        if (imp.getVideo() != null) {
            return ImpMediaType.video;
        }
        if (imp.getAudio() != null) {
            return ImpMediaType.audio;
        }

        return null;
    }

    private static ResetDigitalImpMediaType makeBanner(Banner banner) {
        return makeMediaType(banner.getW(), banner.getH(), null);
    }

    private static ResetDigitalImpMediaType makeVideo(Video video) {
        return makeMediaType(video.getW(), video.getH(), video.getMimes());
    }

    private static ResetDigitalImpMediaType makeAudio(Audio audio) {
        return makeMediaType(null, null, audio.getMimes());
    }

    private static ResetDigitalImpMediaType makeMediaType(Integer width, Integer height, List<String> mimes) {
        final boolean hasValidSizes = isValidSizeValue(width) && isValidSizeValue(height);
        final boolean hasMimes = CollectionUtils.isNotEmpty(mimes);

        if (!hasValidSizes && !hasMimes) {
            return null;
        }

        return ResetDigitalImpMediaType.of(
                hasValidSizes ? List.of(List.of(width, height)) : null,
                hasMimes ? mimes : null);
    }

    private static boolean isValidSizeValue(Integer value) {
        return value != null && value > 0;
    }

    private HttpRequest<ResetDigitalRequest> makeHttpRequest(BidRequest request, ResetDigitalImp resetDigitalImp) {
        final ResetDigitalRequest modifiedRequest = makeRequest(request, resetDigitalImp);

        return HttpRequest.<ResetDigitalRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders(request))
                .impIds(Set.of(resetDigitalImp.getImpId()))
                .body(mapper.encodeToBytes(modifiedRequest))
                .payload(modifiedRequest)
                .build();
    }

    private static ResetDigitalRequest makeRequest(BidRequest request, ResetDigitalImp resetDigitalImp) {
        return ResetDigitalRequest.of(makeSite(request.getSite()), Collections.singletonList(resetDigitalImp));
    }

    private static ResetDigitalSite makeSite(Site site) {
        final String domain = site != null ? site.getDomain() : null;
        final String page = site != null ? site.getPage() : null;

        return ObjectUtils.anyNotNull(domain, page)
                ? ResetDigitalSite.of(domain, page)
                : null;
    }

    private static MultiMap makeHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_REAL_IP_HEADER, device.getIp());
        }

        final Site site = request.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<ResetDigitalRequest> httpCall, BidRequest bidRequest) {
        try {
            final ResetDigitalResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    ResetDigitalResponse.class);
            return Result.withValues(extractBids(bidRequest, bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, ResetDigitalResponse bidResponse) {
        final List<ResetDigitalBid> bids = bidResponse == null
                ? Collections.emptyList()
                : CollectionUtils.emptyIfNull(bidResponse.getBids()).stream().filter(Objects::nonNull).toList();

        if (bids.size() != 1) {
            throw new PreBidException("expected exactly one bid in the response, but got %d".formatted(bids.size()));
        }

        final ResetDigitalBid bid = bids.getFirst();
        final Imp correspondingImp = bidRequest.getImp().stream()
                .filter(imp -> Objects.equals(imp.getId(), bid.getImpId()))
                .findFirst()
                .orElseThrow(() -> new PreBidException(
                        "no matching impression found for ImpID %s".formatted(bid.getImpId())));

        return Collections.singletonList(
                BidderBid.of(makeBid(bid), resolveBidType(correspondingImp), bid.getSeat(), BID_CURRENCY));
    }

    private static Bid makeBid(ResetDigitalBid bid) {
        try {
            return Bid.builder()
                    .id(bid.getBidId())
                    .price(bid.getCpm())
                    .impid(bid.getImpId())
                    .cid(bid.getCid())
                    .crid(bid.getCrid())
                    .adm(bid.getHtml())
                    .w(Integer.parseInt(bid.getW()))
                    .h(Integer.parseInt(bid.getH()))
                    .build();
        } catch (NumberFormatException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidType resolveBidType(Imp imp) throws PreBidException {
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        if (imp.getAudio() != null) {
            return BidType.audio;
        }

        return BidType.banner;
    }
}
