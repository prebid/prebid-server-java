package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AmpRequestFactory {

    private static final String TAG_ID_REQUEST_PARAM = "tag_id";
    private static final String DEBUG_REQUEST_PARAM = "debug";
    private static final String OW_REQUEST_PARAM = "ow";
    private static final String OH_REQUEST_PARAM = "oh";
    private static final String W_REQUEST_PARAM = "w";
    private static final String H_REQUEST_PARAM = "h";
    private static final String MS_REQUEST_PARAM = "ms";
    private static final String CURL_REQUEST_PARAM = "curl";
    private static final String SLOT_REQUEST_PARAM = "slot";
    private static final String TIMEOUT_REQUEST_PARAM = "timeout";

    private final int timeoutAdjustmentMs;
    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;

    public AmpRequestFactory(
            int timeoutAdjustmentMs,
            StoredRequestProcessor storedRequestProcessor,
            AuctionRequestFactory auctionRequestFactory) {
        this.timeoutAdjustmentMs = timeoutAdjustmentMs;
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
    }

    /**
     * Method determines {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    public Future<BidRequest> fromRequest(RoutingContext context) {
        final String tagId = context.request().getParam(TAG_ID_REQUEST_PARAM);

        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
        }

        return storedRequestProcessor.processAmpRequest(tagId)
                .map(bidRequest -> validateStoredBidRequest(tagId, bidRequest))
                .map(bidRequest -> fillExplicitParameters(bidRequest, context))
                .map(bidRequest -> overwriteParameters(bidRequest, context.request()))
                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, context))
                .map(auctionRequestFactory::validateRequest);
    }

    private static BidRequest validateStoredBidRequest(String tagId, BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            throw new InvalidRequestException(
                    String.format("data for tag_id='%s' does not define the required imp array.", tagId));
        }

        final int impSize = imps.size();
        if (impSize > 1) {
            throw new InvalidRequestException(
                    String.format("data for tag_id '%s' includes %d imp elements. Only one is allowed",
                            tagId, impSize));
        }

        if (bidRequest.getExt() == null) {
            throw new InvalidRequestException("AMP requests require Ext to be set");
        }
        return bidRequest;
    }

    /**
     * Updates {@link BidRequest}.ext.prebid.targeting and {@link BidRequest}.ext.prebid.cache.bids with default values
     * if it was not included by user. Updates {@link Imp} security if required to ensure that amp always uses
     * https protocol. Sets {@link BidRequest}.test = 1 if it was passed in context.
     */
    private static BidRequest fillExplicitParameters(BidRequest bidRequest, RoutingContext context) {
        final List<Imp> imps = bidRequest.getImp();
        // Force HTTPS as AMP requires it, but pubs can forget to set it.
        final Imp imp = imps.get(0);
        final Integer secure = imp.getSecure();
        final boolean setSecure = secure == null || secure != 1;

        // AMP won't function unless ext.prebid.targeting and ext.prebid.cache.bids are defined.
        // If the user didn't include them, default those here.
        final ExtBidRequest requestExt;
        try {
            requestExt = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }

        final ExtRequestPrebid prebid = requestExt.getPrebid();

        final boolean setDefaultTargeting;
        final boolean setDefaultCache;

        if (prebid == null) {
            setDefaultTargeting = true;
            setDefaultCache = true;
        } else {
            final ExtRequestTargeting targeting = prebid.getTargeting();
            setDefaultTargeting = targeting == null
                    || targeting.getIncludewinners() == null
                    || targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull();
            final ExtRequestPrebidCache cache = prebid.getCache();
            setDefaultCache = cache == null || cache.getBids().isNull();
        }

        final String debugQueryParam = context.request().getParam(DEBUG_REQUEST_PARAM);
        final Integer test = bidRequest.getTest();
        final boolean setTestParam = !Objects.equals(test, 1) && Objects.equals(debugQueryParam, "1");

        return setDefaultTargeting || setDefaultCache || setSecure || setTestParam
                ? bidRequest.toBuilder()
                .ext(createExtWithDefaults(bidRequest, prebid, setDefaultTargeting, setDefaultCache))
                .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                .test(setTestParam ? Integer.valueOf(1) : test)
                .build()
                : bidRequest;
    }

    private BidRequest overwriteParameters(BidRequest bidRequest, HttpServerRequest request) {
        final List<Format> overwrittenFormats = overwriteBannerFormats(request);
        final Site updatedSite = overwriteSitePage(bidRequest.getSite(), request);
        final String tagId = request.getParam(SLOT_REQUEST_PARAM);

        final Imp updatedImp = updateImp(bidRequest.getImp().get(0), overwrittenFormats, tagId);

        final Integer timeout = parseIntParam(request, TIMEOUT_REQUEST_PARAM);
        final Integer updatedTimeout = timeout != null ? timeout - timeoutAdjustmentMs : null;

        return updateBidRequest(bidRequest, updatedSite, updatedImp, updatedTimeout);
    }

    private List<Format> overwriteBannerFormats(HttpServerRequest request) {
        final Integer ow = parseIntParam(request, OW_REQUEST_PARAM);
        final Integer oh = parseIntParam(request, OH_REQUEST_PARAM);
        final Integer w = parseIntParam(request, W_REQUEST_PARAM);
        final Integer h = parseIntParam(request, H_REQUEST_PARAM);
        final String ms = request.getParam(MS_REQUEST_PARAM);

        Format format = null;
        if (ow != null || oh != null || w != null || h != null) {
            final Integer formatWidth = ow != null ? ow : w;
            final Integer formatHeight = oh != null ? oh : h;
            format = Format.builder().w(formatWidth).h(formatHeight).build();
        }

        List<Format> multiSizeFormats = null;
        if (StringUtils.isNotBlank(ms)) {
            multiSizeFormats = parseMultiSizeParam(ms);
        }

        List<Format> formats = null;
        if (format != null || CollectionUtils.isNotEmpty(multiSizeFormats)) {
            formats = new ArrayList<>();

            if (format != null) {
                formats.add(format);
            }

            if (CollectionUtils.isNotEmpty(multiSizeFormats) && ow == null && oh == null) {
                formats.addAll(multiSizeFormats);
            }
        }
        return formats;
    }

    private Site overwriteSitePage(Site site, HttpServerRequest request) {
        final String canonicalURL = canonicalUrl(request);
        if (StringUtils.isNotBlank(canonicalURL) && site != null) {
            return site.toBuilder().page(canonicalURL).build();
        }
        return null;
    }

    private Imp updateImp(Imp imp, List<Format> formats, String tagId) {
        if (StringUtils.isNotBlank(tagId) || CollectionUtils.isNotEmpty(formats)) {
            return imp.toBuilder()
                    .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                    .banner(updateBanner(imp.getBanner(), formats))
                    .build();
        }
        return null;
    }

    private static Banner updateBanner(Banner banner, List<Format> formats) {
        if (banner == null) {
            return null;
        }
        return banner.toBuilder()
                .format(CollectionUtils.isNotEmpty(formats) ? formats : banner.getFormat())
                .build();
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, Site outgoingSite, Imp outgoingImp, Integer timeout) {
        final boolean isValidTimeout = timeout != null && timeout > 0;
        if (outgoingSite != null || outgoingImp != null || isValidTimeout) {
            return bidRequest.toBuilder()
                    .site(outgoingSite != null ? outgoingSite : bidRequest.getSite())
                    .imp(outgoingImp != null ? Collections.singletonList(outgoingImp) : bidRequest.getImp())
                    .tmax(isValidTimeout ? Long.valueOf(timeout) : bidRequest.getTmax())
                    .build();
        }
        return bidRequest;
    }

    private static Integer parseIntParam(HttpServerRequest request, String name) {
        final String param = request.getParam(name);
        return parseInt(param);
    }

    private static Integer parseInt(String param) {
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Format> parseMultiSizeParam(String ms) {
        final String[] formatStrings = ms.split(",");
        final List<Format> formats = new ArrayList<>();
        for (String format : formatStrings) {
            final String[] widthHeight = format.split("x");
            if (widthHeight.length == 2) {
                formats.add(Format.builder()
                        .w(parseInt(widthHeight[0]))
                        .h(parseInt(widthHeight[1]))
                        .build());
            }
        }
        return formats;
    }

    private String canonicalUrl(HttpServerRequest request) {
        try {
            return HttpUtil.decodeUrl(request.getParam(CURL_REQUEST_PARAM));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Creates updated with default values bidrequest.ext {@link ObjectNode}
     */
    private static ObjectNode createExtWithDefaults(BidRequest bidRequest, ExtRequestPrebid prebid,
                                                    boolean setDefaultTargeting, boolean setDefaultCache) {
        final boolean isPrebidNull = prebid == null;

        return setDefaultTargeting || setDefaultCache
                ? Json.mapper.valueToTree(ExtBidRequest.of(
                ExtRequestPrebid.of(
                        isPrebidNull ? Collections.emptyMap() : prebid.getAliases(),
                        isPrebidNull ? Collections.emptyMap() : prebid.getBidadjustmentfactors(),
                        setDefaultTargeting || isPrebidNull
                                ? createTargetingWithDefaults(prebid) : prebid.getTargeting(),
                        isPrebidNull ? null : prebid.getStoredrequest(),
                        setDefaultCache
                                ? ExtRequestPrebidCache.of(Json.mapper.createObjectNode())
                                : isPrebidNull ? null : prebid.getCache())))
                : bidRequest.getExt();
    }

    /**
     * Creates updated with default values bidrequest.ext.targeting {@link ExtRequestTargeting} if at least one of it's
     * child properties is missed or entire targeting does not exist.
     */
    private static ExtRequestTargeting createTargetingWithDefaults(ExtRequestPrebid prebid) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final boolean isTargetingNull = targeting == null;

        final boolean includeWinners = isTargetingNull || targeting.getIncludewinners() == null
                ? true : targeting.getIncludewinners();

        final JsonNode priceGranularity = isTargetingNull ? null : targeting.getPricegranularity();
        final boolean isPriceGranularityNull = priceGranularity == null || priceGranularity.isNull();

        final JsonNode outgoingPriceGranularityNode = isPriceGranularityNull
                ? Json.mapper.valueToTree(PriceGranularity.DEFAULT.getBuckets())
                : priceGranularity;

        return ExtRequestTargeting.of(outgoingPriceGranularityNode, includeWinners);
    }

}
