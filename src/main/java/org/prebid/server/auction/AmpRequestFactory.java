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
import org.prebid.server.proto.openrtb.ext.request.ExtCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private static final int NO_LIMIT_SPLIT_MODE = -1;

    private final long timeoutAdjustmentMs;
    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;

    public AmpRequestFactory(
            long timeoutAdjustmentMs,
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
                .map(bidRequest -> overrideParameters(bidRequest, context.request()))
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
                    String.format("data for tag_id '%s' includes %d imp elements. Only one is allowed", tagId,
                            impSize));
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
                    || targeting.getIncludebidderkeys() == null
                    || targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull();
            final ExtRequestPrebidCache cache = prebid.getCache();
            setDefaultCache = cache == null || (cache.getBids() == null && cache.getVastxml() == null);
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

    /**
     * This method extracts parameters from http request and overrides corresponding attributes in {@link BidRequest}.
     */
    private BidRequest overrideParameters(BidRequest bidRequest, HttpServerRequest request) {
        final Site updatedSite = overrideSitePage(bidRequest.getSite(), request);
        final Imp updatedImp = overrideImp(bidRequest.getImp().get(0), request);
        final Long updatedTimeout = updateTimeout(request);

        return updateBidRequest(bidRequest, updatedSite, updatedImp, updatedTimeout);
    }

    private static Site overrideSitePage(Site site, HttpServerRequest request) {
        final String canonicalUrl = canonicalUrl(request);
        if (StringUtils.isBlank(canonicalUrl)) {
            return site;
        }

        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        return siteBuilder.page(canonicalUrl).build();
    }

    private Imp overrideImp(Imp imp, HttpServerRequest request) {
        final String tagId = request.getParam(SLOT_REQUEST_PARAM);
        final Banner banner = imp.getBanner();
        final List<Format> overwrittenFormats = banner != null
                ? createOverrideBannerFormats(request, banner.getFormat())
                : null;
        if (StringUtils.isNotBlank(tagId) || CollectionUtils.isNotEmpty(overwrittenFormats)) {
            return imp.toBuilder()
                    .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                    .banner(overrideBanner(imp.getBanner(), overwrittenFormats))
                    .build();
        }
        return null;
    }

    /**
     * Creates formats from request parameters to override origin amp banner formats.
     */
    private static List<Format> createOverrideBannerFormats(HttpServerRequest request, List<Format> formats) {
        final List<Format> overrideFormats;
        final int overrideWidth = parseIntParamOrZero(request, OW_REQUEST_PARAM);
        final int width = parseIntParamOrZero(request, W_REQUEST_PARAM);
        final int overrideHeight = parseIntParamOrZero(request, OH_REQUEST_PARAM);
        final int height = parseIntParamOrZero(request, H_REQUEST_PARAM);
        final String multiSizeParam = request.getParam(MS_REQUEST_PARAM);

        final List<Format> paramsFormats = createFormatsFromParams(overrideWidth, width, overrideHeight, height,
                multiSizeParam);

        if (paramsFormats != null) {
            overrideFormats = paramsFormats;
        } else {
            overrideFormats = updateFormatsFromParams(formats, width, height);
        }

        return overrideFormats;
    }

    /**
     * Create new formats from request parameters.
     */
    private static List<Format> createFormatsFromParams(Integer overrideWidth, Integer width, Integer overrideHeight,
                                                        Integer height, String multiSizeParam) {
        List<Format> overrideFormats = null;
        if (overrideWidth != 0 && overrideHeight != 0) {
            overrideFormats = Collections.singletonList(Format.builder().w(overrideWidth).h(overrideHeight).build());
        } else if (overrideWidth != 0 && height != 0) {
            overrideFormats = Collections.singletonList(Format.builder().w(overrideWidth).h(height).build());
        } else if (width != 0 && overrideHeight != 0) {
            overrideFormats = Collections.singletonList(Format.builder().w(width).h(overrideHeight).build());
        } else {
            final List<Format> multiSizeFormats = StringUtils.isNotBlank(multiSizeParam)
                    ? parseMultiSizeParam(multiSizeParam)
                    : null;
            if (CollectionUtils.isNotEmpty(multiSizeFormats)) {
                overrideFormats = multiSizeFormats;
            } else if (width != 0 && height != 0) {
                overrideFormats = Collections.singletonList(Format.builder().w(width).h(height).build());
            }
        }
        return overrideFormats;
    }

    /**
     * Updates origin amp banner formats from parameters.
     */
    private static List<Format> updateFormatsFromParams(List<Format> formats, Integer width, Integer height) {
        List<Format> updatedFormats = null;
        if (width != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(width).h(format.getH()).build())
                    .collect(Collectors.toList());
        } else if (height != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(format.getW()).h(height).build())
                    .collect(Collectors.toList());
        }
        return updatedFormats;
    }

    private static Banner overrideBanner(Banner banner, List<Format> formats) {
        return banner != null && CollectionUtils.isNotEmpty(formats)
                ? banner.toBuilder().format(formats).build()
                : banner;
    }

    private Long updateTimeout(HttpServerRequest request) {
        try {
            return Long.parseLong(request.getParam(TIMEOUT_REQUEST_PARAM)) - timeoutAdjustmentMs;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, Site outgoingSite, Imp outgoingImp,
                                               Long timeout) {
        final boolean isValidTimeout = timeout != null && timeout > 0;
        if (outgoingSite != null || outgoingImp != null || isValidTimeout) {
            return bidRequest.toBuilder()
                    .site(outgoingSite != null ? outgoingSite : bidRequest.getSite())
                    .imp(outgoingImp != null ? Collections.singletonList(outgoingImp) : bidRequest.getImp())
                    .tmax(isValidTimeout ? timeout : bidRequest.getTmax())
                    .build();
        }
        return bidRequest;
    }

    private static Integer parseIntParamOrZero(HttpServerRequest request, String name) {
        return parseIntOrZero(request.getParam(name));
    }

    private static Integer parseIntOrZero(String param) {
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Format> parseMultiSizeParam(String ms) {
        final String[] formatStrings = ms.split(",", NO_LIMIT_SPLIT_MODE);
        final List<Format> formats = new ArrayList<>();
        for (String format : formatStrings) {
            final String[] widthHeight = format.split("x", NO_LIMIT_SPLIT_MODE);
            if (widthHeight.length != 2) {
                return null;
            }

            final Integer width = parseIntOrZero(widthHeight[0]);
            final Integer height = parseIntOrZero(widthHeight[1]);

            if (width == 0 && height == 0) {
                return null;
            }

            formats.add(Format.builder()
                    .w(width)
                    .h(height)
                    .build());
        }
        return formats;
    }

    private static String canonicalUrl(HttpServerRequest request) {
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
                                ? ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null),
                                ExtRequestPrebidCacheVastxml.of(null))
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

        final JsonNode priceGranularity = isTargetingNull ? null : targeting.getPricegranularity();
        final boolean isPriceGranularityNull = priceGranularity == null || priceGranularity.isNull();
        final JsonNode outgoingPriceGranularityNode = isPriceGranularityNull
                ? Json.mapper.valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT))
                : priceGranularity;

        final ExtCurrency currency = isTargetingNull ? null : targeting.getCurrency();

        final boolean includeWinners = isTargetingNull || targeting.getIncludewinners() == null
                ? true : targeting.getIncludewinners();

        final boolean includeBidderKeys = isTargetingNull || targeting.getIncludebidderkeys() == null
                ? true : targeting.getIncludebidderkeys();

        return ExtRequestTargeting.of(outgoingPriceGranularityNode, currency, includeWinners, includeBidderKeys);
    }
}
