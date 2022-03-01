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
import org.prebid.server.bidder.model.HttpCall;
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
            new TypeReference<ExtPrebid<?, ExtImpYandex>>() {
            };

    private static final String PAGE_ID_MACRO = "{{page_id}}";
    private static final String IMP_ID_MACRO = "{{imp_id}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YandexBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
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

    private ExtImpYandex parseAndValidateImpExt(ObjectNode impExtNode, int index) {
        final ExtImpYandex extImpYandex;
        try {
            extImpYandex = mapper.mapper().convertValue(impExtNode, YANDEX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("imp #%s: %s", index, e.getMessage()));
        }
        final Integer pageId = extImpYandex.getPageId();
        if (pageId == null || pageId == 0) {
            throw new PreBidException(String.format("imp #%s: missing param page_id", index));
        }
        final Integer impId = extImpYandex.getImpId();
        if (impId == null || impId == 0) {
            throw new PreBidException(String.format("imp #%s: missing param imp_id", index));
        }
        return extImpYandex;
    }

    private static Banner makeBanner(Banner banner) {
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
            throw new PreBidException(String.format("Invalid sizes provided for Banner %sx%s", w, h));
        }
        return banner;
    }

    private static Imp modifyImp(Imp imp) {
        if (imp.getBanner() != null) {
            return imp.toBuilder().banner(makeBanner(imp.getBanner())).build();
        }
        if (imp.getXNative() != null || imp.getVideo() != null) {
            return imp;
        }
        throw new PreBidException(String.format("Yandex only supports banner, native and video types. "
                + "Ignoring imp id=%s", imp.getId()));
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

    private String getReferer(BidRequest request) {
        String referer = null;
        final Site site = request.getSite();
        if (site != null) {
            referer = site.getPage();
        }
        return referer;
    }

    private static MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "X-Real-Ip", device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            // TODO: remove
            if (device.getIp().equals("194.111.48.58")) {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, "x-yabs-debug-token", "7671ae5012345678");
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, "x-yabs-debug-output", "json");
            }
        }

        return headers;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final String referer = getReferer(request);
        final List<String> curs = request.getCur();
        final String cur = curs != null && !curs.isEmpty() ? curs.get(0) : "";

        final List<Imp> impList = request.getImp();
        for (int i = 0; i < impList.size(); i++) {
            try {
                final Imp imp = impList.get(i);
                ExtImpYandex extImpYandex = parseAndValidateImpExt(imp.getExt(), i);
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

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
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
                final BidType bidType = resolveImpType(imp);
                if (bidType == null) {
                    throw new PreBidException("Processing an invalid impression; cannot resolve impression type");
                }
                return bidType;
            }
        }
        throw new PreBidException(String.format("Invalid bid imp ID %s does not match any imp IDs from the original "
                + "bid request", bidImpId));
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
        return null;
    }
}
