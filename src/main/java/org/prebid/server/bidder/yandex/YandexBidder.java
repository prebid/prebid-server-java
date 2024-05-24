package org.prebid.server.bidder.yandex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.yandex.ExtImpYandex;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class YandexBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYandex>> YANDEX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PAGE_ID_MACRO = "{{PageId}}";
    private static final String IMP_ID_MACRO = "{{ImpId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YandexBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final String referer = getReferer(request);
        final String currency = getCurrency(request);

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpYandex extImpYandex = parseAndValidateImpExt(imp.getExt(), imp.getId());
                final Imp modifiedImp = modifyImp(imp);
                final String modifiedUrl = modifyUrl(extImpYandex, referer, currency);
                final BidRequest modifiedRequest =
                        request.toBuilder().imp(Collections.singletonList(modifiedImp)).build();
                bidRequests.add(buildHttpRequest(modifiedRequest, modifiedUrl));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(bidRequests, errors);
    }

    private static String getReferer(BidRequest request) {
        return Optional.ofNullable(request.getSite())
                .map(Site::getPage)
                .orElse(null);
    }

    private static String getCurrency(BidRequest request) {
        final List<String> currencies = request.getCur();
        final String currency = CollectionUtils.isNotEmpty(currencies) ? currencies.get(0) : null;

        return StringUtils.defaultString(currency);
    }

    private ExtImpYandex parseAndValidateImpExt(ObjectNode impExtNode, final String impId) {
        final ExtImpYandex extImpYandex;
        try {
            extImpYandex = mapper.mapper().convertValue(impExtNode, YANDEX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp #%s: %s".formatted(impId, e.getMessage()));
        }
        final Integer pageId = extImpYandex.getPageId();
        if (pageId == 0) {
            throw new PreBidException("imp #%s: wrong value for page_id param".formatted(impId));
        }
        final Integer yandexImpId = extImpYandex.getImpId();
        if (yandexImpId == 0) {
            throw new PreBidException("imp #%s: wrong value for imp_id param".formatted(impId));
        }
        return extImpYandex;
    }

    private static Imp modifyImp(Imp imp) {
        if (imp.getBanner() != null) {
            return imp.toBuilder().banner(modifyBanner(imp.getBanner())).build();
        }
        if (imp.getXNative() != null) {
            return imp;
        }
        throw new PreBidException("Yandex only supports banner and native types. Ignoring imp id #%s"
                .formatted(imp.getId()));
    }

    private static Banner modifyBanner(Banner banner) {
        final Integer weight = banner.getW();
        final Integer height = banner.getH();
        final List<Format> format = banner.getFormat();
        if (weight == null || height == null || weight == 0 || height == 0) {
            if (CollectionUtils.isNotEmpty(format)) {
                final Format firstFormat = format.get(0);
                return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
            }
            throw new PreBidException("Invalid sizes provided for Banner %sx%s".formatted(weight, height));
        }
        return banner;
    }

    private String modifyUrl(ExtImpYandex extImpYandex, String referer, String currency) {
        final String resolvedUrl = endpointUrl
                .replace(PAGE_ID_MACRO, HttpUtil.encodeUrl(extImpYandex.getPageId().toString()))
                .replace(IMP_ID_MACRO, HttpUtil.encodeUrl(extImpYandex.getImpId().toString()));
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(resolvedUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid url: %s, error: %s".formatted(endpointUrl, e.getMessage()));
        }
        addParameterIfNotBlank(uriBuilder, "target-ref", referer);
        addParameterIfNotBlank(uriBuilder, "ssp-cur", currency);
        return uriBuilder.toString();
    }

    private static void addParameterIfNotBlank(URIBuilder uriBuilder, String parameter, String value) {
        if (StringUtils.isNotBlank(value)) {
            uriBuilder.addParameter(parameter, value);
        }
    }

    private HttpRequest<BidRequest> buildHttpRequest(BidRequest outgoingRequest, String url) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers(outgoingRequest))
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    private static MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers();
        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_REAL_IP_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), imps), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String bidImpId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (bidImpId.equals(imp.getId())) {
                return resolveImpType(imp);
            }
        }
        throw new PreBidException("Invalid bid imp ID #%s does not match any imp IDs from the original bid request"
                .formatted(bidImpId));
    }

    private static BidType resolveImpType(Imp imp) {
        if (imp.getXNative() != null) {
            return BidType.xNative;
        }
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        if (imp.getAudio() != null) {
            return BidType.audio;
        }
        throw new PreBidException("Processing an invalid impression; cannot resolve impression type for imp #%s"
                .formatted(imp.getId()));
    }
}
