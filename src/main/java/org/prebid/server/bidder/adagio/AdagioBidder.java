package org.prebid.server.bidder.adagio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adagio.ExtImpAdagio;
import org.prebid.server.proto.openrtb.ext.request.adkernel.ExtImpAdkernel;
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.util.HttpUtil;

import java.util.*;
import java.util.stream.Collectors;

public class AdagioBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdagio>> AGADIO_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdagio>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<ExtImpAdagio, List<Imp>> pubToImps = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                processImp(imp, pubToImps);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (hasNoImpressions(pubToImps)) {
            return Result.withErrors(errors);
        }

        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();
        final List<HttpRequest<BidRequest>> httpRequests = pubToImps.entrySet().stream()
                .map(extAndImp -> createHttpRequest(extAndImp, requestBuilder, request.getSite(), request.getApp()))
                .collect(Collectors.toList());

        return Result.of(httpRequests, errors);
    }

    private void processImp(Imp imp, Map<ExtImpAdagio, List<Imp>> pubToImps) {
        validateImp(imp);
        final ExtImpAdagio extImpAdagio = parseAndValidateImpExt(imp);
        dispatchImpression(imp, extImpAdagio, pubToImps);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format(
                    "Invalid imp id=%s. Expected imp.banner or imp.video", imp.getId()));
        }
    }

    private ExtImpAdagio parseAndValidateImpExt(Imp imp) {
        final ExtImpAdagio extImpAdagio;
        try {
            extImpAdagio = mapper.mapper().convertValue(imp.getExt(), AGADIO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final Integer organizationId = extImpAdagio.getOrganizationId();
        if (organizationId == null || organizationId < 1) {
            throw new PreBidException(String.format("Invalid organizationId value: %d. Ignoring imp id=%s",
                    organizationId, imp.getId()));
        }

        if (StringUtils.isBlank(extImpAdagio.getSite())) {
            throw new PreBidException(String.format("Site is empty. Ignoring imp id=%s", imp.getId()));
        }

        if(StringUtils.isBlank(extImpAdagio.getPlacement())){
            throw new PreBidException(String.format("Placement is empty. Ignoring imp id=%s", imp.getId()));
        }
        return extImpAdagio;
    }

    private static void dispatchImpression(Imp imp, ExtImpAdagio extImpAdagio,
                                           Map<ExtImpAdagio, List<Imp>> pubToImp) {
        pubToImp.putIfAbsent(extImpAdagio, new ArrayList<>());
        pubToImp.get(extImpAdagio).add(compatImpression(imp));
    }

    private static Imp compatImpression(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder().ext(null)
                .audio(null)
                .xNative(null);
        return imp.getBanner() != null ? impBuilder.video(null).build() : impBuilder.build();
    }

    private static boolean hasNoImpressions(Map<ExtImpAdagio, List<Imp>> pubToImps) {
        return pubToImps.values().stream()
                .allMatch(CollectionUtils::isEmpty);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

    public AdagioBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    private HttpRequest<BidRequest> createHttpRequest(Map.Entry<ExtImpAdagio, List<Imp>> extAndImp,
                                                      BidRequest.BidRequestBuilder requestBuilder, Site site, App app) {
        final ExtImpAdagio impExt = extAndImp.getKey();
        final String uri = String.format(endpointUrl, impExt.getOrganizationId(), impExt.getSite(), impExt.getPlacement());

        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

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

}
