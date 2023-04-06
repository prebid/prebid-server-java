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
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.yandex.ExtImpYandex;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

        final String cur = getCur(request);
        final String referer = getReferer(request);

        for (Imp imp : request.getImp()) {
            try {
                ExtImpYandex extImpYandex = parseAndValidateImpExt(imp.getExt(), imp.getId());
                final Imp modifiedImp = modifyImp(imp);
                final String modifiedUrl = modifyUrl(extImpYandex, referer, cur);
                final BidRequest modifiedRequest =
                        request.toBuilder().imp(Collections.singletonList(modifiedImp)).build();
                bidRequests.add(buildHttpRequest(modifiedRequest, modifiedUrl));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(bidRequests, errors);
    }

    private String getReferer(BidRequest request) {
        final Site site = request.getSite();
        return site != null ? site.getPage() : null;
    }

    private String getCur(BidRequest request) {
        final List<String> curs = request.getCur();
        return curs != null && !curs.isEmpty() ? curs.get(0) : "";
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
        if (banner == null) {
            return null;
        }
        final Integer w = banner.getW();
        final Integer h = banner.getH();
        final List<Format> format = banner.getFormat();
        if (w == null || h == null || w == 0 || h == 0) {
            if (CollectionUtils.isNotEmpty(format)) {
                final Format firstFormat = format.get(0);
                return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
            }
            throw new PreBidException("Invalid sizes provided for Banner %sx%s".formatted(w, h));
        }
        return banner;
    }

    private String modifyUrl(ExtImpYandex extImpYandex, String referer, String cur) {
        final String resolvedUrl = endpointUrl
                .replace(PAGE_ID_MACRO, HttpUtil.encodeUrl(extImpYandex.getPageId().toString()))
                .replace(IMP_ID_MACRO, HttpUtil.encodeUrl(extImpYandex.getImpId().toString()));
        final StringBuilder uri = new StringBuilder(resolvedUrl);
        if (StringUtils.isNotBlank(referer)) {
            uri.append("&target-ref=").append(HttpUtil.encodeUrl(referer));
        }
        if (StringUtils.isNotBlank(cur)) {
            uri.append("&ssp-cur=").append(cur);
        }
        return uri.toString();
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
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "X-Real-Ip", device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        final List<SeatBid> seatBids = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (seatBids == null) {
            return Collections.emptyList();
        }

        if (seatBids.isEmpty()) {
            throw new PreBidException("SeatBids is empty");
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
        throw new PreBidException(("Invalid bid imp ID #%s does not match any imp IDs from the original "
                + "bid request").formatted(bidImpId));
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
        throw new PreBidException("Processing an invalid impression; cannot resolve impression type");
    }
}
