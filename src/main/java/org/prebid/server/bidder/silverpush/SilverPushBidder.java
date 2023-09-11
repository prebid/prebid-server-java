package org.prebid.server.bidder.silverpush;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.silverpush.ExtImpSilverPush;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SilverPushBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSilverPush>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final BigDecimal BANNER_BIDFLOOR = BigDecimal.valueOf(0.05);
    private static final BigDecimal VIDEO_BIDFLOOR = BigDecimal.valueOf(0.1);
    private static final int DEFAULT_MAX_DURATION = 120;
    private static final int DEFAULT_MIN_DURATION = 0;
    private static final String BIDDER_CONFIG = "sp_pb_ortb";
    private static final String BIDDER_VERSION = "1.0.0";
    private static final String BIDDER_CONFIG_PROPERTY = "bc";
    private static final String PUBLISHER_ID_PROPERTY = "publisherId";
    private static final String EIDS_NODE_PATH = "eids";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final ObjectReader reader;

    public SilverPushBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.reader = mapper.mapper().readerFor(new TypeReference<List<Eid>>() {
        });
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSilverPush impExt = parseImpExt(imp);
                if (StringUtils.isBlank(impExt.getPublisherId())) {
                    throw new PreBidException("Missing publisherId parameter.");
                }
                final BidRequest resolvedBidRequest = resolveBidRequest(request, imp, impExt);
                requests.add(createRequest(resolvedBidRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpSilverPush parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest resolveBidRequest(BidRequest bidRequest, Imp imp, ExtImpSilverPush extImpSilverPush) {
        final String publisherId = extImpSilverPush.getPublisherId();
        return bidRequest.toBuilder()
                .user(resolveUser(bidRequest.getUser()))
                .device(resolveDevice(bidRequest.getDevice()))
                .site(resolveSite(bidRequest.getSite(), publisherId))
                .app(resolveApp(bidRequest.getApp(), publisherId))
                .ext(resolveExtRequest(publisherId))
                .imp(Collections.singletonList(resolveImp(imp, extImpSilverPush)))
                .build();
    }

    private User resolveUser(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        if (extUser == null) {
            return user;
        }

        final List<Eid> dataEids = Optional.ofNullable(extUser.getData())
                .map(dataNode -> dataNode.get(EIDS_NODE_PATH))
                .filter(node -> !node.isMissingNode())
                .map(this::castEids)
                .orElse(null);

        if (!isValidEids(dataEids)) {
            return user;
        }
        final ExtUser resolvedExtUser = ExtUser.builder()
                .eids(dataEids)
                .build();

        return user.toBuilder()
                .ext(resolvedExtUser)
                .build();
    }

    private List<Eid> castEids(JsonNode eidsNode) {
        try {
            return reader.readValue(eidsNode);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValidEids(List<Eid> eids) {
        if (CollectionUtils.isEmpty(eids)) {
            return true;
        }
        for (Eid eid : eids) {
            final List<Uid> uids = eid.getUids();
            if (CollectionUtils.isNotEmpty(uids) && StringUtils.isNotBlank(uids.get(0).getId())) {
                return true;
            }
        }

        return false;
    }

    private static Device resolveDevice(Device device) {
        final String userAgent = device != null ? device.getUa() : null;
        if (StringUtils.isBlank(userAgent)) {
            return device;
        }

        return device.toBuilder()
                .os(SilverPushDeviceTypeResolver.resolveOs(userAgent))
                .devicetype(SilverPushDeviceTypeResolver.resolveDeviceType(userAgent))
                .build();
    }

    private static Site resolveSite(Site site, String publisherId) {
        if (site == null) {
            return null;
        }
        return site.toBuilder()
                .publisher(resolvePublisher(site.getPublisher(), publisherId))
                .build();
    }

    private static Publisher resolvePublisher(Publisher publisher, String publisherId) {
        return publisher != null
                ? publisher.toBuilder().id(publisherId).build()
                : Publisher.builder().id(publisherId).build();
    }

    private static App resolveApp(App app, String publisherId) {
        if (app == null) {
            return null;
        }

        return app.toBuilder()
                .publisher(resolvePublisher(app.getPublisher(), publisherId))
                .build();
    }

    private static ExtRequest resolveExtRequest(String publisherId) {
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty(BIDDER_CONFIG_PROPERTY, TextNode.valueOf(BIDDER_CONFIG + "_" + BIDDER_VERSION));
        extRequest.addProperty(PUBLISHER_ID_PROPERTY, TextNode.valueOf(publisherId));

        return extRequest;
    }

    private static Imp resolveImp(Imp imp, ExtImpSilverPush extImpSilverPush) {
        final Banner banner = resolveBanner(imp.getBanner());
        final boolean bannerPresent = banner != null;
        final Video video = bannerPresent ? null : resolveVideo(imp.getVideo());
        final BigDecimal extBidFloor = extImpSilverPush.getBidFloor();
        final BigDecimal bidFloorFallback = bannerPresent ? BANNER_BIDFLOOR : VIDEO_BIDFLOOR;

        return imp.toBuilder()
                .banner(banner)
                .video(video)
                .bidfloor(BidderUtil.isValidPrice(extBidFloor) ? extBidFloor : bidFloorFallback)
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        if (banner == null || (isNonNegative(banner.getW()) && isNonNegative(banner.getH()))) {
            return banner;
        }

        if (CollectionUtils.isEmpty(banner.getFormat())) {
            throw new PreBidException("No sizes provided for Banner.");
        }

        final Format firstFormat = banner.getFormat().get(0);
        return banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
    }

    private static Video resolveVideo(Video video) {
        if (video == null) {
            return null;
        }
        final Integer minDuration = video.getMinduration();
        final Integer maxDuration = video.getMaxduration();

        if (CollectionUtils.isEmpty(video.getApi())
                || CollectionUtils.isEmpty(video.getMimes())
                || CollectionUtils.isEmpty(video.getProtocols())
                || !isNonNegative(minDuration)) {

            throw new PreBidException("Invalid or missing video field(s)");
        }

        final boolean validMaxDuration = isNonNegative(maxDuration);
        if (validMaxDuration && maxDuration > minDuration) {
            return video;
        }

        final Integer resolvedMaxDuration = validMaxDuration && maxDuration != 0
                ? maxDuration
                : DEFAULT_MAX_DURATION;

        return video.toBuilder()
                .maxduration(resolvedMaxDuration)
                .minduration(resolvedMaxDuration > minDuration ? minDuration : DEFAULT_MIN_DURATION)
                .build();
    }

    private static boolean isNonNegative(Integer value) {
        return value != null && value >= 0;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders())
                .impIds(BidderUtil.impIds(request))
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private static MultiMap makeHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException(
                    "Unable to resolve mediaType " + bid.getMtype() + " for bid: " + bid.getId());
        };
    }
}
