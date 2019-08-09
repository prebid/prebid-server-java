package org.prebid.server.bidder.adkerneladn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AdkernelAdn {@link Bidder} implementation.
 */
public class AdkernelAdnBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdkernelAdn>> ADKERNELADN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdkernelAdn>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public AdkernelAdnBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();

        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        try {
            final List<ExtImpAdkernelAdn> impExts = getAndValidateImpExt(imps);
            final Map<ExtImpAdkernelAdn, List<Imp>> pubToImps = dispatchImpressions(imps, impExts);
            httpRequests.addAll(buildAdapterRequests(bidRequest, pubToImps, endpointUrl));
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        return Result.of(httpRequests, errors);
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add("x-openrtb-version", "2.5");
    }

    private static List<ExtImpAdkernelAdn> getAndValidateImpExt(List<Imp> imps) {
        return imps.stream()
                .map(AdkernelAdnBidder::parseAndValidateAdkernelAdnExt)
                .collect(Collectors.toList());
    }

    private static ExtImpAdkernelAdn parseAndValidateAdkernelAdnExt(Imp imp) {
        final ExtImpAdkernelAdn adkernelAdnExt;
        try {
            adkernelAdnExt = Json.mapper.<ExtPrebid<?, ExtImpAdkernelAdn>>convertValue(imp.getExt(),
                    ADKERNELADN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (adkernelAdnExt.getPubId() == null || adkernelAdnExt.getPubId() < 1) {
            throw new PreBidException(String.format("Invalid pubId value. Ignoring imp id=%s", imp.getId()));
        }
        return adkernelAdnExt;
    }

    /**
     * Group impressions by AdKernel-specific parameters `pubId` & `host`.
     */
    private static Map<ExtImpAdkernelAdn, List<Imp>> dispatchImpressions(List<Imp> imps,
                                                                         List<ExtImpAdkernelAdn> impExts) {
        final Map<ExtImpAdkernelAdn, List<Imp>> result = new HashMap<>();

        for (int i = 0; i < imps.size(); i++) {
            final Imp imp = compatImpression(imps.get(i));
            final ExtImpAdkernelAdn impExt = impExts.get(i);
            result.putIfAbsent(impExt, new ArrayList<>());
            result.get(impExt).add(imp);
        }
        return result;
    }

    /**
     * Alter impression info to comply with adkernel platform requirements.
     */
    private static Imp compatImpression(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        final Banner compatBanner = imp.getBanner();

        impBuilder.ext(null); // do not forward ext to adkernel platform

        if (compatBanner != null && compatBanner.getW() == null && compatBanner.getH() == null) {
            // As banner.w/h are required fields for adkernel adn platform - take the first format entry
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

        return impBuilder.build();
    }

    private static List<HttpRequest<BidRequest>> buildAdapterRequests(BidRequest preBidRequest,
                                                                      Map<ExtImpAdkernelAdn, List<Imp>> pubToImps,
                                                                      String endpointUrl) {
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Map.Entry<ExtImpAdkernelAdn, List<Imp>> entry : pubToImps.entrySet()) {
            final BidRequest outgoingRequest = createBidRequest(preBidRequest, entry.getValue());
            final String body = Json.encode(outgoingRequest);
            result.add(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(buildEndpoint(entry.getKey(), endpointUrl))
                    .body(body)
                    .headers(headers())
                    .payload(outgoingRequest)
                    .build());
        }

        return result;
    }

    private static BidRequest createBidRequest(BidRequest preBidRequest, List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = preBidRequest.toBuilder();

        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp : imps) {
            final Imp modifiedImp = imp.toBuilder()
                    .tagid(imp.getId())
                    .build();

            modifiedImps.add(modifiedImp);
        }
        bidRequestBuilder.imp(modifiedImps);

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

    /**
     * Builds endpoint url based on adapter-specific pub settings from imp.ext.
     */
    private static String buildEndpoint(ExtImpAdkernelAdn impExt, String endpointUrl) {
        final String updatedEndpointUrl;

        if (impExt.getHost() != null) {
            final URL url;
            try {
                url = new URL(endpointUrl);
            } catch (MalformedURLException e) {
                throw new PreBidException(
                        String.format("Error occurred while parsing AdkernelAdn endpoint url: %s", endpointUrl), e);
            }
            final String currentHostAndPort = url.getHost() + (url.getPort() == -1 ? "" : ":" + url.getPort());
            updatedEndpointUrl = endpointUrl.replace(currentHostAndPort, impExt.getHost());
        } else {
            updatedEndpointUrl = endpointUrl;
        }

        return String.format("%s%s", updatedEndpointUrl, impExt.getPubId());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
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
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    /**
     * Figures out which media type this bid is for.
     */
    private static BidType getType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
