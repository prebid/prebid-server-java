package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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
import java.util.stream.Collectors;

public class RichaudienceBidder implements Bidder<BidRequest> {

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
        try {
            validateRequest(request);

            final URL url = extractUrl(request);
            final boolean isSecure = "https".equals(ObjectUtil.getIfNotNull(url, URL::getProtocol));
            final List<Imp> modifiedImps = new ArrayList<>();
            boolean isTest = false;

            for (Imp imp : request.getImp()) {
                validateImp(imp);

                final ExtImpRichaudience richaudienceImp = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, richaudienceImp, isSecure));

                if (!isTest && BooleanUtils.isTrue(richaudienceImp.getTest())) {
                    isTest = true;
                }
            }

            final BidRequest modifiedRequest = modifyBidRequest(request, url, modifiedImps, isTest);
            return Result.withValues(Collections.singletonList(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .headers(HttpUtil.headers())
                    .uri(endpointUrl)
                    .body(mapper.encodeToBytes(modifiedRequest))
                    .payload(modifiedRequest)
                    .build()));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private static void validateRequest(BidRequest bidRequest) throws PreBidException {
        final Device device = bidRequest.getDevice();
        final boolean isDeviceValid = device != null
                && (StringUtils.isNotBlank(device.getIp())
                || StringUtils.isNotBlank(device.getIpv6()));
        if (!isDeviceValid) {
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
        final Banner banner = imp.getBanner();
        final boolean isBannerValid = banner != null
                && (banner.getW() != null || banner.getH() != null
                || CollectionUtils.isNotEmpty(banner.getFormat()));
        if (!isBannerValid) {
            final String errorMessage = String.format("Banner W/H/Format is required. ImpId: %s", imp.getId());
            throw new PreBidException(errorMessage);
        }
    }

    private ExtImpRichaudience parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RICHAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Imp.Id: %s. Invalid ext.", imp.getId()));
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpRichaudience richaudienceImp, boolean isSecure) {
        final String tagId = richaudienceImp.getPid();
        final String bidFloorCur = richaudienceImp.getBidFloorCur();

        return imp.toBuilder()
                .secure(BooleanUtils.toInteger(isSecure))
                .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                .bidfloorcur(StringUtils.isNotBlank(bidFloorCur) ? bidFloorCur : DEFAULT_CURRENCY)
                .build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, URL url, List<Imp> imps, boolean isTest) {
        final BidRequest.BidRequestBuilder builder = bidRequest.toBuilder()
                .imp(imps)
                .test(BooleanUtils.toInteger(isTest));

        final Site site = bidRequest.getSite();
        if (url != null && StringUtils.isBlank(site.getDomain())) {
            builder.site(site.toBuilder().domain(url.getHost()).build());
        }

        return builder.build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
