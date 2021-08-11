package org.prebid.server.bidder.adagio;

import com.iab.openrtb.request.*;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
import org.prebid.server.util.HttpUtil;

import java.util.*;

public class AdagioBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdagioBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = endpointUrl;
        this.mapper = mapper;
    }

    private static MultiMap resolveHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
        }
        return headers;
    }

    private static Integer resolveSecure(Site site) {
        return site != null && StringUtils.isNotBlank(site.getPage()) && site.getPage().startsWith("https") ? 1 : 0;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Integer secure = resolveSecure(request.getSite());
        final List<BidderError> errors = new ArrayList<>();
        Imp imp = request.getImp().get(0);
            try {

                validateImp(imp);
                imp = (modifyImp(imp, secure));

            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }

        if (errors.size() > 0) {
            return Result.withErrors(errors);
        }

        return Result.withValue(createRequest(extImpBetween, request, imps));
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, Imp imp) {
        final String url =
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .headers(resolveHeaders(request.getDevice(), request.getSite()))
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
                        .build();
    }

    private static Imp modifyImp(Imp imp, Integer secure) {
        final Banner resolvedBanner = resolveBanner(imp.getBanner());

        return imp.toBuilder()
                .banner(resolvedBanner)
                .secure(secure)
                .build();
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format(
                    "Invalid imp id=%s. Expected imp.banner or imp.video", imp.getId()));
        }
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




}
