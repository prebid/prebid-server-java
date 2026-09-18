package org.prebid.server.bidder.rtbstack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.rtbstack.ExtImpRtbStack;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RtbStackBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRtbStack>> RTBSTACK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String REGION_MACRO = "{{Region}}";
    private static final String SSP_ID_MACRO = "{{SspID}}";
    private static final String ZONE_ID_MACRO = "{{ZoneID}}";
    private static final String PARTNER_ID_MACRO = "{{PartnerId}}";
    private static final String REGION_HOST_SUFFIX = "-adx-admixer";
    private static final Set<String> VALID_REGIONS = Set.of("us", "eu", "sg");
    private static final String CUSTOM_PARAMS_FIELD = "customParams";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RtbStackBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> impsByRoute = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpRtbStack extImp = parseImpExt(imp);
                impsByRoute.computeIfAbsent(extImp.getRoute(), route -> new ArrayList<>())
                        .add(modifyImp(imp, extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final Site site = modifySite(request.getSite());
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Map.Entry<String, List<Imp>> routeImps : impsByRoute.entrySet()) {
            try {
                final String endpoint = buildEndpointUrl(routeImps.getKey());
                final BidRequest outgoingRequest = request.toBuilder()
                        .imp(routeImps.getValue())
                        .site(site)
                        .build();
                httpRequests.add(BidderUtil.defaultRequest(outgoingRequest, endpoint, mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpRtbStack parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RTBSTACK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Wrong RTBStack bidder ext");
        }
    }

    private Imp modifyImp(Imp imp, ExtImpRtbStack extImp) {
        return imp.toBuilder()
                .tagid(extImp.getTagId())
                .ext(makeImpExt(extImp.getCustomParams()))
                .build();
    }

    private ObjectNode makeImpExt(Map<String, Object> customParams) {
        final ObjectNode impExt = mapper.mapper().createObjectNode();
        if (MapUtils.isNotEmpty(customParams)) {
            impExt.set(CUSTOM_PARAMS_FIELD, mapper.mapper().valueToTree(customParams));
        }
        return impExt;
    }

    private static Site modifySite(Site site) {
        return site == null || StringUtils.isNotEmpty(site.getDomain())
                ? site
                : site.toBuilder().domain(resolveDomain(site.getPage())).build();
    }

    private static String resolveDomain(String page) {
        final String host = HttpUtil.getHostFromUrl(page);
        return StringUtils.isNotEmpty(host) ? host : page;
    }

    private String buildEndpointUrl(String route) {
        final URIBuilder routeUri;
        try {
            routeUri = new URIBuilder(route);
        } catch (URISyntaxException e) {
            throw new PreBidException("invalid route URL: " + e.getMessage());
        }

        final String region = extractRegion(routeUri.getHost());
        final List<NameValuePair> queryParams = routeUri.getQueryParams();
        final String client = firstQueryParam(queryParams, "client");
        final String endpoint = firstQueryParam(queryParams, "endpoint");
        final String ssp = firstQueryParam(queryParams, "ssp");

        if (StringUtils.isEmpty(client) || StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(ssp)) {
            throw new PreBidException("route URL must contain client, endpoint, and ssp query parameters");
        }

        return endpointUrl
                .replace(REGION_MACRO, region)
                .replace(SSP_ID_MACRO, HttpUtil.encodeUrl(ssp))
                .replace(ZONE_ID_MACRO, HttpUtil.encodeUrl(endpoint))
                .replace(PARTNER_ID_MACRO, HttpUtil.encodeUrl(client));
    }

    private static String extractRegion(String hostname) {
        for (String hostPart : StringUtils.defaultString(hostname).split("\\.")) {
            if (hostPart.endsWith(REGION_HOST_SUFFIX)) {
                final String region = StringUtils.removeEnd(hostPart, REGION_HOST_SUFFIX).toLowerCase();
                if (VALID_REGIONS.contains(region)) {
                    return region;
                }
            }
        }
        throw new PreBidException("unable to extract valid region from route URL hostname");
    }

    private static String firstQueryParam(List<NameValuePair> queryParams, String name) {
        return queryParams.stream()
                .filter(param -> name.equals(param.getName()))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = getBidType(bid, errors);
        return bidType != null
                ? BidderBid.of(bid, bidType, currency)
                : null;
    }

    private static BidType getBidType(Bid bid, List<BidderError> errors) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> {
                errors.add(BidderError.badServerResponse(
                        "unsupported MType " + bid.getMtype() + " for bid " + bid.getImpid()));
                yield null;
            }
        };
    }
}
