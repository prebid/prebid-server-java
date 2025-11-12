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
import org.prebid.server.bidder.sparteo.util.SparteoUtil;
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
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SparteoBidder implements Bidder<BidRequest> {

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

        final Site site = request.getSite();
        final App app = request.getApp();

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(modifiedImps)
                .site(modifySite(site, networkId))
                .app(modifyApp(app, networkId))
                .build();

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

    private Site modifySite(Site site, String networkId) {
        if (site == null) {
            return site;
        }

        final Publisher originalPublisher = site.getPublisher() != null
                ? site.getPublisher()
                : Publisher.builder().build();

        final Publisher modifiedPublisher = modifyPublisher(originalPublisher, networkId);

        return site.toBuilder().publisher(modifiedPublisher).build();
    }

    private App modifyApp(App app, String networkId) {
        if (app == null) {
            return app;
        }

        final Publisher originalPublisher = app.getPublisher() != null
                ? app.getPublisher()
                : Publisher.builder().build();

        final Publisher modifiedPublisher = modifyPublisher(originalPublisher, networkId);

        return app.toBuilder().publisher(modifiedPublisher).build();
    }

    private Publisher modifyPublisher(Publisher originalPublisher, String networkId) {
        final ExtPublisher originalExt = originalPublisher.getExt();
        final ExtPublisher modifiedExt = originalExt == null
                ? ExtPublisher.empty()
                : mapper.mapper().convertValue(originalExt, ExtPublisher.class);

        final ObjectNode paramsNode = ensureParamsNode(modifiedExt);
        paramsNode.put("networkId", networkId);

        return originalPublisher.toBuilder()
                .ext(modifiedExt)
                .build();
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

        if (UNKNOWN_VALUE.equals(siteDomain)) {
            errors.add(BidderError.badInput(
                    "Domain not found. Missing the site.domain or the site.page field"));
        }

        if (UNKNOWN_VALUE.equals(bundle)) {
            errors.add(BidderError.badInput(
                    "Bundle not found. Missing the app.bundle field."));
        }

        return resolveEndpoint(siteDomain, appDomain, networkId, bundle);
    }

    private String resolveSiteDomain(Site site) {
        if (site == null) {
            return null;
        }

        return Optional.of(site)
                .map(Site::getDomain)
                .map(SparteoUtil::normalizeHostname)
                .filter(StringUtils::isNotEmpty)
                .or(() -> Optional.ofNullable(site.getPage())
                        .map(SparteoUtil::normalizeHostname)
                        .filter(StringUtils::isNotEmpty))
                .orElse(UNKNOWN_VALUE);
    }

    private String resolveAppDomain(App app) {
        if (app == null) {
            return null;
        }

        return Optional.of(app)
                .map(App::getDomain)
                .map(SparteoUtil::normalizeHostname)
                .filter(StringUtils::isNotEmpty)
                .orElse(UNKNOWN_VALUE);
    }

    private String resolveBundle(App app) {
        if (app == null) {
            return null;
        }

        return Optional.ofNullable(app.getBundle())
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .filter(bundle -> !"null".equalsIgnoreCase(bundle))
                .orElse(UNKNOWN_VALUE);
    }

    private String resolveEndpoint(String siteDomain, String appDomain, String networkId, String bundle) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpointUrl);
            if (StringUtils.isNotBlank(networkId)) {
                uriBuilder.addParameter("network_id", networkId);
            }
            if (StringUtils.isNotBlank(siteDomain)) {
                uriBuilder.addParameter("site_domain", siteDomain);
            }
            if (StringUtils.isNotBlank(appDomain)) {
                uriBuilder.addParameter("app_domain", appDomain);
            }
            if (StringUtils.isNotBlank(bundle)) {
                uriBuilder.addParameter("bundle", bundle);
            }
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException("Failed to build endpoint URL", e);
        }
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
