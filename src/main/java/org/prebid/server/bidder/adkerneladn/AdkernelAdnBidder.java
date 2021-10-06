package org.prebid.server.bidder.adkerneladn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
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
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdkernelAdnBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdkernelAdn>> ADKERNELADN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdkernelAdn>>() {
            };
    private static final String DEFAULT_DOMAIN = "tag.adkernel.com";
    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_PUBLISHER_ID_MACRO = "{{PublisherID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdkernelAdnBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> validImps = bidRequest.getImp();
        final List<BidderError> errors = new ArrayList<>();

        final Map<Imp, ExtImpAdkernelAdn> impWithExts = getAndValidateImpExt(validImps, errors);
        final Map<ExtImpAdkernelAdn, List<Imp>> pubToImps = dispatchImpressions(impWithExts, errors);
        if (MapUtils.isEmpty(pubToImps)) {
            return Result.withErrors(errors);
        }

        return Result.of(buildAdapterRequests(bidRequest, pubToImps), errors);
    }

    private Map<Imp, ExtImpAdkernelAdn> getAndValidateImpExt(List<Imp> imps, List<BidderError> errors) {
        final Map<Imp, ExtImpAdkernelAdn> validImpsWithExts = new HashMap<>();
        for (Imp imp : imps) {
            try {
                validateImp(imp);
                validImpsWithExts.put(imp, parseAndValidateAdkernelAdnExt(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return validImpsWithExts;
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid imp with id=%s. Expected imp.banner or imp.video",
                    imp.getId()));
        }
    }

    private ExtImpAdkernelAdn parseAndValidateAdkernelAdnExt(Imp imp) {
        final ExtImpAdkernelAdn adkernelAdnExt;
        try {
            adkernelAdnExt = mapper.mapper().convertValue(imp.getExt(), ADKERNELADN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (adkernelAdnExt.getPubId() == null || adkernelAdnExt.getPubId() < 1) {
            throw new PreBidException(String.format("Invalid pubId value. Ignoring imp id=%s", imp.getId()));
        }
        return adkernelAdnExt;
    }

    private static Map<ExtImpAdkernelAdn, List<Imp>> dispatchImpressions(Map<Imp, ExtImpAdkernelAdn> impsWithExts,
                                                                         List<BidderError> errors) {
        final Map<ExtImpAdkernelAdn, List<Imp>> result = new HashMap<>();

        for (Imp key : impsWithExts.keySet()) {
            final Imp imp;
            try {
                imp = compatImpression(key);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            final ExtImpAdkernelAdn impExt = impsWithExts.get(key);
            result.putIfAbsent(impExt, new ArrayList<>());
            result.get(impExt).add(imp);
        }

        return result;
    }

    private static Imp compatImpression(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        impBuilder.ext(null);

        final Banner banner = imp.getBanner();
        if (banner != null) {
            compatBannerImpression(impBuilder, banner);
        }
        return impBuilder
                .audio(null)
                .xNative(null)
                .build();
    }

    private static void compatBannerImpression(Imp.ImpBuilder impBuilder, Banner compatBanner) {
        if (compatBanner.getW() == null && compatBanner.getH() == null) {
            final List<Format> compatBannerFormat = compatBanner.getFormat();

            if (CollectionUtils.isEmpty(compatBannerFormat)) {
                throw new PreBidException("Expected at least one banner.format entry or explicit w/h");
            }

            final Format format = compatBannerFormat.get(0);
            final Banner.BannerBuilder bannerBuilder = compatBanner.toBuilder();

            if (compatBannerFormat.size() > 1) {
                bannerBuilder.format(compatBannerFormat.subList(1, compatBannerFormat.size()));
            } else {
                bannerBuilder.format(Collections.emptyList());
            }
            bannerBuilder
                    .w(format.getW())
                    .h(format.getH());

            impBuilder.banner(bannerBuilder.build());
        }

        impBuilder.video(null);
    }

    private List<HttpRequest<BidRequest>> buildAdapterRequests(BidRequest preBidRequest,
                                                               Map<ExtImpAdkernelAdn, List<Imp>> pubToImps) {
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Map.Entry<ExtImpAdkernelAdn, List<Imp>> entry : pubToImps.entrySet()) {
            result.add(createRequest(entry.getKey(), entry.getValue(), preBidRequest));
        }

        return result;
    }

    private HttpRequest<BidRequest> createRequest(ExtImpAdkernelAdn extImp, List<Imp> imps, BidRequest preBidRequest) {
        final BidRequest outgoingRequest = createBidRequest(preBidRequest, imps);
        final String body = mapper.encode(outgoingRequest);
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(buildEndpoint(extImp))
                .body(body)
                .headers(headers())
                .payload(outgoingRequest)
                .build();
    }

    private static BidRequest createBidRequest(BidRequest preBidRequest, List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = preBidRequest.toBuilder()
                .imp(imps);

        final Site site = preBidRequest.getSite();
        if (site != null) {
            bidRequestBuilder.site(site.toBuilder().publisher(null).domain("").build());
        }

        final App app = preBidRequest.getApp();
        if (app != null) {
            bidRequestBuilder.app(app.toBuilder().publisher(null).build());
        }
        return bidRequestBuilder.build();
    }

    private String buildEndpoint(ExtImpAdkernelAdn impExt) {
        final String impHost = impExt.getHost();
        final String host = StringUtils.isNotBlank(impHost) ? impHost : DEFAULT_DOMAIN;

        return endpointUrl
                .replace(URL_HOST_MACRO, host)
                .replace(URL_PUBLISHER_ID_MACRO, impExt.getPubId().toString());
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() != 1) {
            throw new PreBidException(String.format("Invalid SeatBids count: %d", bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getBanner() != null) {
                return BidType.banner;
            }
        }
        return BidType.video;
    }
}
