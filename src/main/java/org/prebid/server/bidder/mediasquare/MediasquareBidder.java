package org.prebid.server.bidder.mediasquare;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.mediasquare.request.MediasquareBanner;
import org.prebid.server.bidder.mediasquare.request.MediasquareCode;
import org.prebid.server.bidder.mediasquare.request.MediasquareFloor;
import org.prebid.server.bidder.mediasquare.request.MediasquareGdpr;
import org.prebid.server.bidder.mediasquare.request.MediasquareMediaTypes;
import org.prebid.server.bidder.mediasquare.request.MediasquareRequest;
import org.prebid.server.bidder.mediasquare.request.MediasquareSupport;
import org.prebid.server.bidder.mediasquare.response.MediasquareBid;
import org.prebid.server.bidder.mediasquare.response.MediasquareResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.mediasquare.ExtImpMediasquare;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class MediasquareBidder implements Bidder<MediasquareRequest> {

    private static final String SIZE_FORMAT = "%dx%d";
    private static final TypeReference<ExtPrebid<?, ExtImpMediasquare>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MediasquareBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<MediasquareRequest>>> makeHttpRequests(BidRequest request) {
        final List<MediasquareCode> codes = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpMediasquare extImp = parseImpExt(imp);
                final MediasquareCode mediasquareCode = makeCode(request, imp, extImp);
                if (isCodeValid(mediasquareCode)) {
                    codes.add(mediasquareCode);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (codes.isEmpty()) {
            return Result.withErrors(errors);
        }

        final MediasquareRequest outgoingRequest = makeRequest(request, codes);

        final HttpRequest<MediasquareRequest> httpRequest = HttpRequest.<MediasquareRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .impIds(BidderUtil.impIds(request))
                .build();

        return Result.of(List.of(httpRequest), errors);
    }

    private ExtImpMediasquare parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("can not parse imp.ext" + e.getMessage());
        }
    }

    private static MediasquareCode makeCode(BidRequest bidRequest, Imp imp, ExtImpMediasquare extImp) {
        final MediasquareMediaTypes mediaTypes = makeMediaTypes(imp);
        final Map<String, MediasquareFloor> floors = mediaTypes == null
                ? null
                : makeFloors(MediasquareFloor.of(imp.getBidfloor(), imp.getBidfloorcur()), mediaTypes);

        return MediasquareCode.builder()
                .adUnit(imp.getTagid())
                .auctionId(bidRequest.getId())
                .bidId(imp.getId())
                .code(extImp.getCode())
                .owner(extImp.getOwner())
                .mediaTypes(mediaTypes)
                .floor(floors)
                .build();
    }

    private static MediasquareMediaTypes makeMediaTypes(Imp imp) {
        final Video video = imp.getVideo();
        final Banner banner = imp.getBanner();
        final Native xNative = imp.getXNative();

        if (video == null && banner == null && xNative == null) {
            return null;
        }

        return MediasquareMediaTypes.builder()
                .banner(makeBanner(banner))
                .video(video)
                .nativeRequest(xNative != null ? xNative.getRequest() : null)
                .build();
    }

    private static MediasquareBanner makeBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        final List<List<Integer>> sizes = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(banner.getFormat())) {
            for (Format format : banner.getFormat()) {
                sizes.add(List.of(format.getW(), format.getH()));
            }
        } else {
            sizes.add(List.of(banner.getW(), banner.getH()));
        }

        return MediasquareBanner.of(sizes);
    }

    private static Map<String, MediasquareFloor> makeFloors(MediasquareFloor floor, MediasquareMediaTypes mediaTypes) {
        final Map<String, MediasquareFloor> floors = new HashMap<>();

        final Video video = mediaTypes.getVideo();
        final MediasquareBanner banner = mediaTypes.getBanner();
        final String xNative = mediaTypes.getNativeRequest();

        if (video != null) {
            if (video.getW() != null && video.getH() != null) {
                final String videoSize = SIZE_FORMAT.formatted(video.getW(), video.getH());
                floors.put(videoSize, floor);
            }
            floors.put("*", floor);
        }

        if (banner != null) {
            for (List<Integer> format: banner.getSizes()) {
                floors.put(SIZE_FORMAT.formatted(format.get(0), format.get(1)), floor);
            }
        }

        if (xNative != null) {
            floors.put("*", floor);
        }

        return MapUtils.isNotEmpty(floors) ? floors : null;
    }

    private static boolean isCodeValid(MediasquareCode code) {
        final MediasquareMediaTypes mediaTypes = code.getMediaTypes();
        return mediaTypes != null && ObjectUtils.anyNotNull(
                mediaTypes.getBanner(), mediaTypes.getVideo(), mediaTypes.getNativeRequest());
    }

    private static ExtRegsDsa getDsa(Regs regs) {
        return Optional.ofNullable(regs)
                .map(Regs::getExt)
                .map(ExtRegs::getDsa)
                .orElse(null);
    }

    private MediasquareRequest makeRequest(BidRequest bidRequest, List<MediasquareCode> codes) {
        final User user = bidRequest.getUser();
        final Regs regs = bidRequest.getRegs();

        return MediasquareRequest.builder()
                .codes(codes)
                .dsa(getDsa(regs))
                .gdpr(makeGdpr(user, regs))
                .type("pbs")
                .support(MediasquareSupport.of(bidRequest.getDevice(), bidRequest.getApp()))
                .test(Objects.equals(bidRequest.getTest(), 1))
                .build();
    }

    private static MediasquareGdpr makeGdpr(User user, Regs regs) {
        final boolean gdprApplies = Optional.ofNullable(regs)
                .map(Regs::getGdpr)
                .map(gdpr -> gdpr == 1)
                .orElse(false);
        final String consent = user != null ? user.getConsent() : null;
        return MediasquareGdpr.of(gdprApplies, consent);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<MediasquareRequest> httpCall, BidRequest bidRequest) {
        try {
            final MediasquareResponse response = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    MediasquareResponse.class);
            return Result.withValues(extractBids(response));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Failed to decode response: " + e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(MediasquareResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getResponses())) {
            return Collections.emptyList();
        }

        return response.getResponses().stream()
                .filter(Objects::nonNull)
                .map(this::makeBidderBid)
                .collect(Collectors.toList());
    }

    private BidderBid makeBidderBid(MediasquareBid bid) {
        final BidType bidType = getBidType(bid);
        return BidderBid.of(makeBid(bid, bidType), bidType, bid.getCurrency());
    }

    private Bid makeBid(MediasquareBid bid, BidType bidType) {
        return Bid.builder()
                .id(bid.getId())
                .impid(bid.getBidId())
                .price(bid.getCpm())
                .adm(bid.getAd())
                .adomain(bid.getAdomain())
                .w(bid.getWidth())
                .h(bid.getHeight())
                .crid(bid.getCreativeId())
                .mtype(bidType.ordinal() + 1)
                .burl(bid.getBurl())
                .ext(getBidExt(bid))
                .build();
    }

    private ObjectNode getBidExt(MediasquareBid bid) {
        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder()
                .advertiserDomains(bid.getAdomain() != null ? bid.getAdomain() : null)
                .mediaType(getBidType(bid).getName())
                .build();

        final ExtBidPrebid prebid = ExtBidPrebid.builder().meta(meta).build();

        final ObjectNode bidExt = mapper.mapper().createObjectNode();
        if (bid.getDsa() != null) {
            bidExt.set("dsa", bid.getDsa());
        }
        bidExt.set("prebid", mapper.mapper().valueToTree(prebid));

        return bidExt;
    }

    private static BidType getBidType(MediasquareBid bid) {
        if (bid.getVideo() != null) {
            return BidType.video;
        }
        if (bid.getNativeResponse() != null) {
            return BidType.xNative;
        }
        return BidType.banner;
    }
}
