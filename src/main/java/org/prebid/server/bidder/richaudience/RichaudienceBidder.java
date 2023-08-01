package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.util.ObjectUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RichaudienceBidder implements Bidder<BidRequest> {

    private static final int BID_TEST_REQUEST = 1;
    private static final String OPENRTB_VERSION = "2.5";
    private static final String DEVICE_IP = "11.222.33.44";
    private static final String DEFAULT_CURRENCY = "USD";
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
        final URL url = extractUrl(request);
        final boolean isSecure = "https".equals(ObjectUtil.getIfNotNull(url, URL::getProtocol));
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        boolean isTest = false;

        try {
            validateRequest(request);
            for (Imp imp : request.getImp()) {
                validateImp(imp);

                final ExtImpRichaudience extImpRichaudience = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpRichaudience, isSecure);

                if (!isTest && BooleanUtils.isTrue(extImpRichaudience.getTest())) {
                    isTest = true;
                }

                httpRequests.add(createHttpRequest(
                        modifyBidRequest(request, url, modifiedImp, isTest)));
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValues(httpRequests);
    }

    private static void validateRequest(BidRequest bidRequest) throws PreBidException {
        final Device device = bidRequest.getDevice();
        if (device == null || StringUtils.isAllBlank(device.getIp(), device.getIpv6())) {
            throw new PreBidException("Device IP is required.");
        }
    }

    private static URL extractUrl(BidRequest bidRequest) {
        try {
            return new URL(ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getPage));
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static void validateImp(Imp imp) throws PreBidException {
        if (!isBannerSizesPresent(imp.getBanner())) {
            throw new PreBidException("Banner W/H/Format is required. ImpId: " + imp.getId());
        }
    }

    private static boolean isBannerSizesPresent(Banner banner) {
        return banner != null && (ObjectUtils.anyNotNull(banner.getW(), banner.getH())
                || CollectionUtils.isNotEmpty(banner.getFormat()));
    }

    private ExtImpRichaudience parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RICHAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext. Imp.Id: " + imp.getId());
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpRichaudience extImpRichaudience, boolean isSecure) {
        final String tagId = extImpRichaudience.getPid();
        final String extBidFloorCur = extImpRichaudience.getBidFloorCur();
        final String impBidFloorCur = imp.getBidfloorcur();

        final String bidFloorCur = StringUtils.defaultIfBlank(extBidFloorCur,
                StringUtils.defaultIfBlank(impBidFloorCur, DEFAULT_CURRENCY));

        return imp.toBuilder()
                .secure(BooleanUtils.toInteger(isSecure))
                .tagid(StringUtils.defaultIfBlank(tagId, imp.getTagid()))
                .bidfloorcur(bidFloorCur)
                .build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, URL url, Imp imp, boolean isTest) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder().imp(Collections.singletonList(imp));

        if (isTest) {
            requestBuilder.test(BID_TEST_REQUEST).device(Device.builder().ip(DEVICE_IP).build());
        }

        final Site site = bidRequest.getSite();
        if (url != null && StringUtils.isBlank(site.getDomain())) {
            requestBuilder.site(site.toBuilder().domain(url.getHost()).build());
        }

        return requestBuilder.build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(resolveHeaders())
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

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid,
                        resolvedBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType resolvedBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                return BidType.banner;
            }
        }
        return BidType.banner;
    }
}
