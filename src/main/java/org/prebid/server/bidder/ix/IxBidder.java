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
    private final JacksonMapper mapper;

    public IxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
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
                makeRequests(bidRequest, imp, prioritizedRequests, regularRequests);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<BidRequest> modifiedRequests = Stream.concat(prioritizedRequests.stream(), regularRequests.stream())
                .limit(REQUEST_LIMIT)
                .collect(Collectors.toList());
        if (modifiedRequests.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        final List<HttpRequest<BidRequest>> httpRequests = modifiedRequests.stream()
                .map(request -> HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(mapper.encode(request))
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build())
                .collect(Collectors.toList());

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Ix supports only Banner type. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }
    }

    private void makeRequests(BidRequest bidRequest, Imp imp, List<BidRequest> prioritizedRequests,
                              List<BidRequest> regularRequests) {
        List<Format> formats = imp.getBanner().getFormat();
        if (CollectionUtils.isEmpty(formats)) {
            formats = makeFormatFromBannerWidthAndHeight(imp);
        }
        final ExtImpIx extImpIx = parseAndValidateImpExt(imp);
        final List<BidRequest> modifiedBidRequests = createBidRequest(bidRequest, imp, formats, extImpIx);
        if (!modifiedBidRequests.isEmpty()) {
            if (modifiedBidRequests.size() == 1) {
                prioritizedRequests.addAll(modifiedBidRequests);
            } else {
                prioritizedRequests.add(modifiedBidRequests.get(0));
                regularRequests.addAll(modifiedBidRequests.subList(1, modifiedBidRequests.size()));
            }
        }
    }

    private static List<Format> makeFormatFromBannerWidthAndHeight(Imp imp) {
        final Banner banner = imp.getBanner();
        return Collections.singletonList(
                Format.builder().w(banner.getW()).h(banner.getH()).build());
    }

    private ExtImpIx parseAndValidateImpExt(Imp imp) {
        final ExtImpIx extImpIx;
        try {
            extImpIx = mapper.mapper().convertValue(imp.getExt(),
                    IX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpIx.getSiteId())) {
            throw new PreBidException("Missing siteId param");
        }
        return extImpIx;
    }

    private static List<BidRequest> createBidRequest(BidRequest bidRequest, Imp imp,
                                                     List<Format> formats, ExtImpIx extImpIx) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        final Banner.BannerBuilder bannerBuilder = imp.getBanner().toBuilder();
        final List<Format> limitedFormats = formats.size() > REQUEST_LIMIT
                ? formats.subList(0, REQUEST_LIMIT)
                : formats;

        final List<BidRequest> requests = new ArrayList<>();
        for (Format format : limitedFormats) {
            bannerBuilder.format(Collections.singletonList(format))
                    .w(format.getW())
                    .h(format.getH());
            impBuilder.banner(bannerBuilder.build());
            impBuilder.tagid(imp.getId());

            requests.add(requestBuilder
                    .site(modifySite(bidRequest, extImpIx))
                    .imp(Collections.singletonList(impBuilder.build()))
                    .build());
        }
        return requests;
    }

    private static Site modifySite(BidRequest bidRequest, ExtImpIx extImpIx) {
        final Site site = bidRequest.getSite();
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        return siteBuilder.publisher(Publisher.builder()
                .id(extImpIx.getSiteId()).build()).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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
