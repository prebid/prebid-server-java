package org.prebid.server.bidder.sparteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.sparteo.ExtImpSparteo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SparteoBidder implements Bidder<BidRequest> {

    private static final String NETWORK_ID_MACRO = "{{NetworkId}}";
    private static final String SITE_DOMAIN_QUERY_MACRO = "{{SiteDomainQuery}}";
    private static final String APP_DOMAIN_QUERY_MACRO = "{{AppDomainQuery}}";
    private static final String BUNDLE_QUERY_MACRO = "{{BundleQuery}}";
    private static final String UNKNOWN_VALUE = "unknown";

    private static final TypeReference<ExtPrebid<?, ExtImpSparteo>> TYPE_REFERENCE =
            new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SparteoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrlSyntax(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        String networkId = null;

        for (Imp imp : request.getImp()) {
            if (networkId == null) {
                try {
                    networkId = parseExtImp(imp).getNetworkId();
                } catch (PreBidException e) {
                    errors.add(BidderError.badInput(
                            "ignoring imp id=%s, error processing ext: %s".formatted(
                                    imp.getId(), e.getMessage())));
                }
            }
            final ObjectNode modifiedExt = modifyImpExt(imp);
            modifiedImps.add(imp.toBuilder().ext(modifiedExt).build());
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest.BidRequestBuilder builder = request.toBuilder().imp(modifiedImps);

        final Site site = request.getSite();
        final App app = request.getApp();

        if (site != null) {
            builder.site(modifySite(site, networkId, mapper));
        } else if (app != null) {
            builder.app(modifyApp(app, networkId, mapper));
        }

        final BidRequest outgoingRequest = builder.build();

        final String finalEndpointUrl = replaceMacros(site, app, networkId, errors);
        final HttpRequest<BidRequest> call = BidderUtil.defaultRequest(outgoingRequest, finalEndpointUrl, mapper);

        return Result.of(Collections.singletonList(call), errors);
    }

    private ExtImpSparteo parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext");
        }
    }

    private ObjectNode modifyImpExt(Imp imp) {
        final ObjectNode modifiedImpExt = imp.getExt().deepCopy();
        final ObjectNode sparteoNode = modifiedImpExt.putObject("sparteo");
        final JsonNode bidderJsonNode = modifiedImpExt.remove("bidder");
        sparteoNode.set("params", bidderJsonNode);
        return modifiedImpExt;
    }

    private Site modifySite(Site site, String networkId, JacksonMapper mapper) {
        if (site == null) {
            return site;
        }

        final Publisher originalPublisher = site.getPublisher() != null
                ? site.getPublisher()
                : Publisher.builder().build();

        final ExtPublisher originalExt = originalPublisher.getExt();
        final ExtPublisher modifiedExt = originalExt != null
                ? ExtPublisher.of(originalExt.getPrebid())
                : ExtPublisher.empty();

        if (originalExt != null) {
            mapper.fillExtension(modifiedExt, originalExt);
        }

        final ObjectNode paramsNode = ensureParamsNode(modifiedExt);
        paramsNode.put("networkId", networkId);

        final Publisher modifiedPublisher = originalPublisher.toBuilder()
                .ext(modifiedExt)
                .build();

        return site.toBuilder().publisher(modifiedPublisher).build();
    }

    private App modifyApp(App app, String networkId, JacksonMapper mapper) {
        if (app == null) {
            return app;
        }

        final Publisher originalPublisher = app.getPublisher() != null
                ? app.getPublisher()
                : Publisher.builder().build();

        final ExtPublisher originalExt = originalPublisher.getExt();
        final ExtPublisher modifiedExt = originalExt != null
                ? ExtPublisher.of(originalExt.getPrebid())
                : ExtPublisher.empty();

        if (originalExt != null) {
            mapper.fillExtension(modifiedExt, originalExt);
        }

        final ObjectNode paramsNode = ensureParamsNode(modifiedExt);
        paramsNode.put("networkId", networkId);

        final Publisher modifiedPublisher = originalPublisher.toBuilder()
                .ext(modifiedExt)
                .build();

        return app.toBuilder().publisher(modifiedPublisher).build();
    }

    private ObjectNode ensureParamsNode(ExtPublisher extPublisher) {
        final JsonNode paramsProperty = extPublisher.getProperty("params");
        if (paramsProperty != null && paramsProperty.isObject()) {
            return (ObjectNode) paramsProperty;
        }
        final ObjectNode paramsNode = mapper.mapper().createObjectNode();
        extPublisher.addProperty("params", paramsNode);
        return paramsNode;
    }

    private String replaceMacros(Site site, App app, String networkId, List<BidderError> errors) {
        final String siteDomain = resolveSiteDomain(site);
        final String appDomain = resolveAppDomain(app);
        final String bundle = resolveBundle(app);

        if (site != null) {
            if (UNKNOWN_VALUE.equals(siteDomain)) {
                errors.add(BidderError.badInput(
                        "Domain not found. Missing the site.domain or the site.page field"));
            }
        }

        if (UNKNOWN_VALUE.equals(bundle)) {
            errors.add(BidderError.badInput(
                    "Bundle not found. Missing the app.bundle field."));
        }
        return resolveEndpoint(siteDomain, appDomain, networkId, bundle);
    }

    private static String normalizeHostname(String host) {
        String h = StringUtils.trimToEmpty(host);
        if (h.isEmpty()) {
            return "";
        }

        String hostname = null;
        try {
            hostname = new URI(h).getHost();
        } catch (URISyntaxException e) {
        }

        if (StringUtils.isNotEmpty(hostname)) {
            h = hostname;
        } else {
            if (h.contains(":")) {
                h = StringUtils.substringBefore(h, ":");
            } else {
                h = StringUtils.substringBefore(h, "/");
            }
        }

        h = h.toLowerCase();
        h = StringUtils.removeStart(h, "www.");
        h = StringUtils.removeEnd(h, ".");

        return "null".equals(h) ? "" : h;
    }

    private String resolveSiteDomain(Site site) {
        if (site != null) {
            final String siteDomain = normalizeHostname(site.getDomain());
            if (siteDomain != null && !siteDomain.isEmpty()) {
                return siteDomain;
            } else {
                final String fromPage = normalizeHostname(site.getPage());
                if (fromPage != null && !fromPage.isEmpty()) {
                    return fromPage;
                }
            }
            return UNKNOWN_VALUE;
        }

        return null;
    }

    private String resolveAppDomain(App app) {
        if (app != null) {
            final String appDomain = normalizeHostname(app.getDomain());
            if (appDomain != null && !appDomain.isEmpty()) {
                return appDomain;
            }
            return UNKNOWN_VALUE;
        }

        return null;
    }

    private String resolveBundle(App app) {
        if (app == null) {
            return null;
        }

        final String rawBundle = app.getBundle();
        if (rawBundle == null || rawBundle.isBlank()) {
            return UNKNOWN_VALUE;
        }

        final String bundle = rawBundle.trim();
        if ("null".equalsIgnoreCase(bundle)) {
            return UNKNOWN_VALUE;
        }

        return bundle;
    }

    private String resolveEndpoint(String siteDomain, String appDomain, String networkId, String bundle) {
        final String siteDomainQuery = StringUtils.isNotBlank(siteDomain)
                ? "&site_domain=" + HttpUtil.encodeUrl(siteDomain)
                : "";
        final String appDomainQuery = StringUtils.isNotBlank(appDomain)
                ? "&app_domain=" + HttpUtil.encodeUrl(appDomain)
                : "";
        final String bundleQuery = StringUtils.isNotBlank(bundle)
                ? "&bundle=" + HttpUtil.encodeUrl(bundle)
                : "";

        return endpointUrl
            .replace(NETWORK_ID_MACRO, StringUtils.defaultString(networkId))
            .replace(BUNDLE_QUERY_MACRO, bundleQuery)
            .replace(SITE_DOMAIN_QUERY_MACRO, siteDomainQuery)
            .replace(APP_DOMAIN_QUERY_MACRO, appDomainQuery);
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

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid);

            final Integer mtype = switch (bidType) {
                case banner -> 1;
                case video -> 2;
                case xNative -> 4;
                default -> null;
            };

            final Bid bidWithMtype = mtype != null ? bid.toBuilder().mtype(mtype).build() : bid;

            return BidderBid.of(bidWithMtype, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(Bid bid) throws PreBidException {
        final BidType bidType = Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("prebid"))
                .filter(JsonNode::isObject)
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElseThrow(() -> new PreBidException(
                        "Failed to parse bid mediatype for impression \"%s\"".formatted(bid.getImpid())));

        if (bidType == BidType.audio) {
            throw new PreBidException(
                    "Audio bid type not supported by this adapter for impression id: %s".formatted(bid.getImpid()));
        }

        return bidType;
    }

    private ExtBidPrebid parseExtBidPrebid(JsonNode prebidNode) {
        try {
            return mapper.mapper().treeToValue(prebidNode, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
