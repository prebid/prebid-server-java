package org.prebid.server.bidder.adkernel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.adkernel.ExtImpAdkernel;
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

/**
 * Adkernel {@link Bidder} implementation.
 */
public class AdkernelBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdkernel>> ADKERNEL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdkernel>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public AdkernelBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<ExtImpAdkernel, List<Imp>> pubToImps = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                final ExtImpAdkernel extImpAdkernel = parseAndValidateImpExt(imp);
                dispatchImpression(imp, extImpAdkernel, pubToImps);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (hasNoImpressions(pubToImps)) {
            return Result.of(null, errors);
        }

        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();
        final List<HttpRequest<BidRequest>> httpRequests = pubToImps.entrySet().stream()
                .map(extAndImp -> createHttpRequest(extAndImp, requestBuilder, request.getSite(), request.getApp()))
                .collect(Collectors.toList());

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format(
                    "Invalid imp id=%s. Expected imp.banner or imp.video", imp.getId()));
        }
    }

    private ExtImpAdkernel parseAndValidateImpExt(Imp imp) {
        final ExtImpAdkernel extImpAdkernel;
        try {
            extImpAdkernel = mapper.mapper().convertValue(imp.getExt(), ADKERNEL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final Integer zoneId = extImpAdkernel.getZoneId();
        if (zoneId == null || zoneId < 1) {
            throw new PreBidException(String.format("Invalid zoneId value: %d. Ignoring imp id=%s",
                    zoneId, imp.getId()));
        }

        if (StringUtils.isBlank(extImpAdkernel.getHost())) {
            throw new PreBidException(String.format("Host is empty. Ignoring imp id=%s", imp.getId()));
        }
        return extImpAdkernel;
    }

    //Group impressions by AdKernel-specific parameters `zoneId` & `host`
    private static void dispatchImpression(Imp imp, ExtImpAdkernel extImpAdkernel,
                                           Map<ExtImpAdkernel, List<Imp>> pubToImp) {
        pubToImp.putIfAbsent(extImpAdkernel, new ArrayList<>());
        pubToImp.get(extImpAdkernel).add(compatImpression(imp));
    }

    //Alter impression info to comply with adkernel platform requirements
    private static Imp compatImpression(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder().ext(null) //do not forward ext to adkernel platform
                .audio(null)
                .xNative(null);
        return imp.getBanner() != null ? impBuilder.video(null).build() : impBuilder.build();
    }

    private static boolean hasNoImpressions(Map<ExtImpAdkernel, List<Imp>> pubToImps) {
        return pubToImps.values().stream()
                .mapToLong(Collection::size)
                .sum() == 0;
    }

    private HttpRequest<BidRequest> createHttpRequest(Map.Entry<ExtImpAdkernel, List<Imp>> extAndImp,
                                                      BidRequest.BidRequestBuilder requestBuilder, Site site, App app) {
        final ExtImpAdkernel impExt = extAndImp.getKey();
        final String uri = String.format(endpointTemplate, impExt.getHost(), impExt.getZoneId());

        final MultiMap headers = HttpUtil.headers()
                .add("x-openrtb-version", "2.5");

        final BidRequest outgoingRequest = createBidRequest(extAndImp.getValue(), requestBuilder, site, app);
        final String body = mapper.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(headers)
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    private static BidRequest createBidRequest(
            List<Imp> imps, BidRequest.BidRequestBuilder requestBuilder, Site site, App app) {

        requestBuilder.imp(imps);

        if (site != null) {
            requestBuilder.site(site.toBuilder().publisher(null).build());
        } else {
            requestBuilder.app(app.toBuilder().publisher(null).build());
        }
        return requestBuilder.build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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
            if (imp.getId().equals(impId) && imp.getBanner() != null) {
                return BidType.banner;
            }
        }
        return BidType.video;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
