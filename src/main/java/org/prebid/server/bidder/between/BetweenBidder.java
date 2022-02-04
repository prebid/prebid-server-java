package org.prebid.server.bidder.between;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BetweenBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBetween>> BETWEEN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String URL_HOST_MACRO = "{{Host}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BetweenBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Integer secure = resolveSecure(request.getSite());
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        ExtImpBetween extImpBetween = null;
        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                extImpBetween = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, secure));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.withValue(createRequest(extImpBetween, request, modifiedImps));
    }

    private static Integer resolveSecure(Site site) {
        return site != null && StringUtils.isNotBlank(site.getPage()) && site.getPage().startsWith("https") ? 1 : 0;
    }

    private static void validateImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (imp.getBanner() == null) {
            throw new PreBidException("Request needs to include a Banner object");
        }
        if (banner.getW() == null && banner.getH() == null) {
            if (CollectionUtils.isEmpty(banner.getFormat())) {
                throw new PreBidException("Need at least one size to build request");
            }
        }
    }

    private ExtImpBetween parseImpExt(Imp imp) {
        final ExtImpBetween extImpBetween;
        final String missingParamErrorMessage = "required BetweenSSP parameter %s is missing in impression with id: %s";
        try {
            extImpBetween = mapper.mapper().convertValue(imp.getExt(), BETWEEN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Missing bidder ext in impression with id: %s", imp.getId()));
        }
        if (StringUtils.isBlank(extImpBetween.getHost())) {
            throw new PreBidException(String.format(missingParamErrorMessage, "host", imp.getId()));
        }
        if (StringUtils.isBlank(extImpBetween.getPublisherId())) {
            throw new PreBidException(String.format(missingParamErrorMessage, "publisher_id", imp.getId()));
        }
        return extImpBetween;
    }

    private static Imp modifyImp(Imp imp, Integer secure) {
        final Banner resolvedBanner = resolveBanner(imp.getBanner());

        return imp.toBuilder()
                .banner(resolvedBanner)
                .secure(secure)
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        if (banner.getW() == null && banner.getH() == null) {
            final List<Format> bannerFormat = banner.getFormat();
            final Format firstFormat = bannerFormat.get(0);
            final List<Format> formatSkipFirst = bannerFormat.subList(1, bannerFormat.size());
            return banner.toBuilder()
                    .format(formatSkipFirst)
                    .w(firstFormat.getW())
                    .h(firstFormat.getH())
                    .build();
        }
        return banner;
    }

    private HttpRequest<BidRequest> createRequest(ExtImpBetween extImpBetween, BidRequest request, List<Imp> imps) {
        final String url = endpointUrl.replace(URL_HOST_MACRO, extImpBetween.getHost())
                .replace("{{PublisherId}}", HttpUtil.encodeUrl(extImpBetween.getPublisherId()));
        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();

        return
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .headers(resolveHeaders(request.getDevice(), request.getSite()))
                        .payload(outgoingRequest)
                        .body(mapper.encodeToBytes(outgoingRequest))
                        .build();
    }

    private static MultiMap resolveHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            final Integer dnt = device.getDnt();
            if (dnt != null) {
                headers.add(HttpUtil.DNT_HEADER, dnt.toString());
            }
        }
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
