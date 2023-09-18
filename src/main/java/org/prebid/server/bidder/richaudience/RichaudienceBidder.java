package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RichaudienceBidder implements Bidder<BidRequest> {

    private static final int BID_TEST_REQUEST = 1;
    private static final String OPENRTB_VERSION = "2.5";
    private static final String DEVICE_IP = "11.222.33.44";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String HTTPS = "https";
    private static final String TAG_ID_KEY = "tagId";
    private static final TypeReference<ExtPrebid<?, ExtImpRichaudience>> RICHAUDIENCE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RichaudienceBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        try {
            validateRequest(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                final ExtImpRichaudience extImp = parseImpExt(imp);
                final BidRequest modifiedBidRequest = makeRequest(request, imp, extImp);
                httpRequests.add(makeHttpRequest(modifiedBidRequest, Set.of(imp.getId())));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return httpRequests.isEmpty()
                ? Result.withErrors(errors)
                : Result.of(httpRequests, errors);
    }

    private static void validateRequest(BidRequest bidRequest) throws PreBidException {
        final Device device = bidRequest.getDevice();
        if (device == null || StringUtils.isAllBlank(device.getIp(), device.getIpv6())) {
            throw new PreBidException("Device IP is required.");
        }
    }

    private static void validateImp(Imp imp) throws PreBidException {
        if (imp.getBanner() != null && !isBannerSizesPresent(imp.getBanner())) {
            throw new PreBidException("Banner W/H/Format is required. ImpId: " + imp.getId());
        }

        if (imp.getVideo() != null && !isVideoSizesPresent(imp.getVideo())) {
            throw new PreBidException("Video W and H are required. ImpId: " + imp.getId());
        }
    }

    private static boolean isBannerSizesPresent(Banner banner) {
        return ObjectUtils.anyNotNull(banner.getW(), banner.getH())
                || CollectionUtils.isNotEmpty(banner.getFormat());
    }

    private static boolean isVideoSizesPresent(Video video) {
        return video.getW() != null && video.getW() != 0 && video.getH() != null && video.getH() != 0;
    }

    private ExtImpRichaudience parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RICHAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext. Imp.Id: " + imp.getId());
        }
    }

    private static BidRequest makeRequest(BidRequest request, Imp imp, ExtImpRichaudience extImp) {
        final Site originalSite = request.getSite();

        final Optional<URL> urlOptional = extractUrl(originalSite);
        final boolean isSecure = urlOptional.map(URL::getProtocol).map(HTTPS::equals).orElse(false);
        final Imp modifiedImp = modifyImp(imp, extImp, isSecure);
        final Site modifiedSite = urlOptional
                .map(url -> modifySite(originalSite, imp.getTagid(), url))
                .orElseGet(() -> modifySite(originalSite, imp.getTagid()));
        final App modifiedApp = modifyApp(request.getApp(), imp.getTagid());
        final boolean isTest = BooleanUtils.isTrue(extImp.getTest());

        return request.toBuilder()
                .imp(List.of(modifiedImp))
                .site(modifiedSite)
                .app(modifiedApp)
                .test(isTest ? BID_TEST_REQUEST : null)
                .device(isTest ? request.getDevice().toBuilder().ip(DEVICE_IP).build() : request.getDevice())
                .build();
    }

    private static Site modifySite(Site originalSite, String tagId, URL url) {
        return originalSite.toBuilder()
                .keywords(TAG_ID_KEY + "=" + tagId)
                .domain(StringUtils.isBlank(originalSite.getDomain()) ? url.getHost() : originalSite.getDomain())
                .build();
    }

    private static Site modifySite(Site originalSite, String tagId) {
        return Optional.ofNullable(originalSite)
                .map(site -> site.toBuilder().keywords(TAG_ID_KEY + "=" + tagId).build())
                .orElse(originalSite);
    }

    private static App modifyApp(App originalApp, String tagId) {
        return Optional.ofNullable(originalApp)
                .map(app -> app.toBuilder().keywords(TAG_ID_KEY + "=" + tagId).build())
                .orElse(originalApp);
    }

    private static Optional<URL> extractUrl(Site site) {
        return Optional.ofNullable(site).map(Site::getPage).map(page -> {
            try {
                return new URL(page);
            } catch (MalformedURLException e) {
                return null;
            }
        });
    }

    private static Imp modifyImp(Imp imp, ExtImpRichaudience extImp, boolean isSecure) {
        final String tagId = extImp.getPid();
        final String extBidFloorCur = extImp.getBidFloorCur();
        final String impBidFloorCur = imp.getBidfloorcur();

        final String bidFloorCur = StringUtils.defaultIfBlank(
                extBidFloorCur,
                StringUtils.defaultIfBlank(impBidFloorCur, DEFAULT_CURRENCY));

        return imp.toBuilder()
                .secure(BooleanUtils.toInteger(isSecure))
                .tagid(StringUtils.defaultIfBlank(tagId, imp.getTagid()))
                .bidfloorcur(bidFloorCur)
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, Set<String> impIds) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(resolveHeaders())
                .impIds(impIds)
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    private MultiMap resolveHeaders() {
        return HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .flatMap(seatBid -> seatBid.getBid().stream())
                .filter(Objects::nonNull)
                //todo: is it fine to return a bid with unknown bid type? (as the PBS Go does)
                .filter(bid -> impMap.containsKey(bid.getImpid()))
                .map(bid -> makeBid(bidResponse, impMap, bid))
                .toList();
    }

    private static BidderBid makeBid(BidResponse bidResponse, Map<String, Imp> impMap, Bid bid) {
        final Imp imp = impMap.get(bid.getImpid());
        final BidType bidType = resolveBidType(imp);
        final Bid.BidBuilder builder = bid.toBuilder();

        if (bidType == BidType.video) {
            builder.w(imp.getVideo().getW());
            builder.h(imp.getVideo().getH());
        }
        return BidderBid.of(builder.build(), bidType, bidResponse.getCur());
    }

    private static BidType resolveBidType(Imp imp) {
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        return BidType.banner;
    }
}
