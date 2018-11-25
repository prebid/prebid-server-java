package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ix.ExtImpIx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ix {@link Bidder} implementation.
 */
public class IxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpIx>> IX_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpIx>>() {
            };

    // maximum number of bid requests
    private static final int REQUEST_LIMIT = 20;
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public IxBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            return Result.emptyWithError(BidderError.badInput("ix doesn't support apps"));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidRequest> prioritizedRequests = new ArrayList<>();
        final List<BidRequest> regularRequests = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            if (prioritizedRequests.size() == REQUEST_LIMIT) {
                break;
            }
            try {
                validateImp(imp);
                final ExtImpIx extImpIx = parseAndValidateIxExt(imp);
                final BidRequest.BidRequestBuilder requestBuilder = modifyRequest(bidRequest, extImpIx);
                makeRequests(requestBuilder, imp, prioritizedRequests, regularRequests);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<BidRequest> modifiedRequests = Stream.concat(prioritizedRequests.stream(), regularRequests.stream())
                .limit(REQUEST_LIMIT)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(modifiedRequests)) {
            return Result.of(Collections.emptyList(), errors);
        }

        final List<HttpRequest<BidRequest>> httpRequests = modifiedRequests.stream()
                .map(request -> HttpRequest.of(HttpMethod.POST, endpointUrl, Json.encode(request),
                        BidderUtil.headers(), request))
                .collect(Collectors.toList());

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Ix supports only Banner type. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }
    }

    private static ExtImpIx parseAndValidateIxExt(Imp imp) {
        final ExtImpIx extImpIx;
        try {
            extImpIx = Json.mapper.<ExtPrebid<?, ExtImpIx>>convertValue(imp.getExt(),
                    IX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpIx.getSiteId())) {
            throw new PreBidException("Missing siteId param");
        }
        return extImpIx;
    }

    private static BidRequest.BidRequestBuilder modifyRequest(BidRequest bidRequest, ExtImpIx extImpIx) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        final Site site = bidRequest.getSite();
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        final Site modifiedSite = siteBuilder.publisher(Publisher.builder()
                .id(extImpIx.getSiteId()).build()).build();
        return requestBuilder.site(modifiedSite);
    }

    private static void makeRequests(BidRequest.BidRequestBuilder requestBuilder, Imp imp,
                                     List<BidRequest> prioritizedRequests, List<BidRequest> regularRequests) {
        final Banner banner = imp.getBanner();
        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        final Banner.BannerBuilder bannerBuilder = imp.getBanner().toBuilder();
        impBuilder.tagid(imp.getId());

        List<Format> formats = banner.getFormat();
        if (CollectionUtils.isEmpty(formats)) {
            bannerBuilder.format(Collections.singletonList(
                    Format.builder().w(banner.getW()).h(banner.getH()).build()));
            impBuilder.banner(bannerBuilder.build());
            requestBuilder.imp(Collections.singletonList(impBuilder.build()));
            prioritizedRequests.add(requestBuilder.build());
        } else {
            if (formats.size() > REQUEST_LIMIT) {
                formats = formats.subList(0, REQUEST_LIMIT);
            }
            boolean isFirstSize = true;
            for (Format format : formats) {
                bannerBuilder.format(Collections.singletonList(format))
                        .w(format.getW())
                        .h(format.getH());
                impBuilder.banner(bannerBuilder.build());

                final BidRequest modifiedRequest = requestBuilder.imp(
                        Collections.singletonList(impBuilder.build())).build();
                if (isFirstSize) {
                    prioritizedRequests.add(modifiedRequest);
                    isFirstSize = false;
                } else {
                    regularRequests.add(modifiedRequest);
                }
            }
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
