package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AmpRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AmpRequestFactory.class);

    private static final String TAG_ID_REQUEST_PARAM = "tag_id";
    private static final String TARGETING_REQUEST_PARAM = "targeting";
    private static final String DEBUG_REQUEST_PARAM = "debug";
    private static final String OW_REQUEST_PARAM = "ow";
    private static final String OH_REQUEST_PARAM = "oh";
    private static final String W_REQUEST_PARAM = "w";
    private static final String H_REQUEST_PARAM = "h";
    private static final String MS_REQUEST_PARAM = "ms";
    private static final String CURL_REQUEST_PARAM = "curl";
    private static final String ACCOUNT_REQUEST_PARAM = "account";
    private static final String SLOT_REQUEST_PARAM = "slot";
    private static final String TIMEOUT_REQUEST_PARAM = "timeout";
    private static final String GDPR_CONSENT_PARAM = "gdpr_consent";
    private static final String CONSENT_PARAM = "consent_string";
    private static final int NO_LIMIT_SPLIT_MODE = -1;

    private final StoredRequestProcessor storedRequestProcessor;
    private final AuctionRequestFactory auctionRequestFactory;
    private final FpdResolver fpdResolver;
    private final TimeoutResolver timeoutResolver;
    private final JacksonMapper mapper;

    public AmpRequestFactory(StoredRequestProcessor storedRequestProcessor, AuctionRequestFactory auctionRequestFactory,
                             FpdResolver fpdResolver, TimeoutResolver timeoutResolver, JacksonMapper mapper) {

        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String tagId = routingContext.request().getParam(TAG_ID_REQUEST_PARAM);
        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
        }
        return createBidRequest(routingContext, tagId)
                .compose(bidRequest ->
                        auctionRequestFactory.toAuctionContext(routingContext, bidRequest, startTime, timeoutResolver));
    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> createBidRequest(RoutingContext context, String tagId) {
        return storedRequestProcessor.processAmpRequest(tagId)
                .map(bidRequest -> validateStoredBidRequest(tagId, bidRequest))
                .map(bidRequest -> fillExplicitParameters(bidRequest, context))
                .map(bidRequest -> overrideParameters(bidRequest, context.request()))
                .map(bidRequest -> auctionRequestFactory.fillImplicitParameters(bidRequest, context, timeoutResolver))
                .map(auctionRequestFactory::validateRequest);
    }

    /**
     * Throws {@link InvalidRequestException} in case of invalid {@link BidRequest}.
     */
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

        if (bidRequest.getApp() != null) {
            throw new InvalidRequestException("request.app must not exist in AMP stored requests.");
        }

        if (bidRequest.getExt() == null) {
            throw new InvalidRequestException("AMP requests require Ext to be set");
        }
        return bidRequest;
    }

    /**
     * Updates {@link BidRequest}.ext.prebid.targeting and {@link BidRequest}.ext.prebid.cache.bids with default values
     * if it was not included by user. Updates {@link Imp} security if required to ensure that amp always uses
     * https protocol. Sets {@link BidRequest}.test = 1 if it was passed in {@link RoutingContext}.
     */
    private BidRequest fillExplicitParameters(BidRequest bidRequest, RoutingContext context) {
        final List<Imp> imps = bidRequest.getImp();
        // Force HTTPS as AMP requires it, but pubs can forget to set it.
        final Imp imp = imps.get(0);
        final Integer secure = imp.getSecure();
        final boolean setSecure = secure == null || secure != 1;

        final ExtRequestPrebid prebid = bidRequest.getExt().getPrebid();

        // AMP won't function unless ext.prebid.targeting and ext.prebid.cache.bids are defined.
        // If the user didn't include them, default those here.
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
            setDefaultCache = cache == null || cache.equals(ExtRequestPrebidCache.EMPTY);
        }

        final Integer debugQueryParam = debugFromQueryStringParam(context);

        final Integer test = bidRequest.getTest();
        final Integer updatedTest = debugQueryParam != null && !Objects.equals(debugQueryParam, test)
                ? debugQueryParam
                : null;

        final Integer debug = prebid != null ? prebid.getDebug() : null;
        final Integer updatedDebug = debugQueryParam != null && !Objects.equals(debugQueryParam, debug)
                ? debugQueryParam
                : null;

        final BidRequest result;
        if (setSecure || setDefaultTargeting || setDefaultCache || updatedTest != null || updatedDebug != null) {
            result = bidRequest.toBuilder()
                    .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                    .test(ObjectUtils.defaultIfNull(updatedTest, test))
                    .ext(extRequest(bidRequest, prebid, setDefaultTargeting, setDefaultCache, updatedDebug))
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    /**
     * Returns debug flag from request query string if it is equal to either 0 or 1, or null if otherwise.
     */
    private static Integer debugFromQueryStringParam(RoutingContext context) {
        final String debug = context.request().getParam(DEBUG_REQUEST_PARAM);
        return Objects.equals(debug, "1") ? Integer.valueOf(1) : Objects.equals(debug, "0") ? 0 : null;
    }

    /**
     * Extracts parameters from http request and overrides corresponding attributes in {@link BidRequest}.
     */
    private BidRequest overrideParameters(BidRequest bidRequest, HttpServerRequest request) {
        final String requestConsentParam = request.getParam(CONSENT_PARAM);
        final String requestGdprConsentParam = request.getParam(GDPR_CONSENT_PARAM);
        final String consentString = ObjectUtils.firstNonNull(requestConsentParam, requestGdprConsentParam);

        String gdprConsent = null;
        String ccpaConsent = null;
        if (StringUtils.isNotBlank(consentString)) {
            gdprConsent = TcfDefinerService.isGdprConsentValid(consentString) ? consentString : null;
            ccpaConsent = Ccpa.isValid(consentString) ? consentString : null;

            if (StringUtils.isAllBlank(gdprConsent, ccpaConsent)) {
                logger.debug("Amp request parameter consent_string or gdpr_consent have invalid format: {0}",
                        consentString);
            }
        }

        final String requestTargeting = request.getParam(TARGETING_REQUEST_PARAM);
        final Targeting targeting = parseTargeting(requestTargeting);
        final ObjectNode targetingNode = (ObjectNode) readTargeting(requestTargeting);

        final Site updatedSite = overrideSite(bidRequest.getSite(), request, targeting);
        final Imp updatedImp = overrideImp(bidRequest.getImp().get(0), request, targetingNode);
        final Long updatedTimeout = overrideTimeout(bidRequest.getTmax(), request);
        final User updatedUser = overrideUser(bidRequest.getUser(), gdprConsent, targeting);
        final Regs updatedRegs = overrideRegs(bidRequest.getRegs(), ccpaConsent);
        final ObjectNode updatedExtBidRequest = overrideExtBidRequest(bidRequest.getExt(), targeting);

        final BidRequest result;
        if (updatedSite != null || updatedImp != null || updatedTimeout != null || updatedUser != null
                || updatedRegs != null || updatedExtBidRequest != null) {
            result = bidRequest.toBuilder()
                    .site(updatedSite != null ? updatedSite : bidRequest.getSite())
                    .imp(updatedImp != null ? Collections.singletonList(updatedImp) : bidRequest.getImp())
                    .tmax(updatedTimeout != null ? updatedTimeout : bidRequest.getTmax())
                    .user(updatedUser != null ? updatedUser : bidRequest.getUser())
                    .regs(updatedRegs != null ? updatedRegs : bidRequest.getRegs())
                    .ext(updatedExtBidRequest != null ? updatedExtBidRequest : bidRequest.getExt())
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    private JsonNode readTargeting(String requestTargeting) {
        try {
            return mapper.mapper().readTree(requestTargeting);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error reading targeting json %s", e.getMessage()));
        }
    }

    private Targeting parseTargeting(String jsonTargeting) {
        try {
            final String decodedJsonTargeting = HttpUtil.decodeUrl(jsonTargeting);
            return decodedJsonTargeting == null
                    ? Targeting.empty()
                    : mapper.decodeValue(decodedJsonTargeting, Targeting.class);
        } catch (DecodeException e) {
            throw new InvalidRequestException(String.format("Error decoding targeting from url: %s", e.getMessage()));
        }
    }

    private Site overrideSite(Site site, HttpServerRequest request, Targeting targeting) {
        final String canonicalUrl = canonicalUrl(request);
        final String accountId = request.getParam(ACCOUNT_REQUEST_PARAM);

        final boolean hasSite = site != null;
        final ExtSite siteExt = hasSite ? site.getExt() : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.getAmp() == null;
        final ObjectNode fpdSiteNode = targeting.getSite();

        if (StringUtils.isNotBlank(canonicalUrl) || StringUtils.isNotBlank(accountId) || shouldSetExtAmp
                || fpdSiteNode != null) {
            final Site.SiteBuilder siteBuilder = hasSite ? site.toBuilder() : Site.builder();
            if (StringUtils.isNotBlank(canonicalUrl)) {
                siteBuilder.page(canonicalUrl);
            }
            if (StringUtils.isNotBlank(accountId)) {
                final Publisher publisher = hasSite ? site.getPublisher() : null;
                final Publisher.PublisherBuilder publisherBuilder = publisher != null
                        ? publisher.toBuilder() : Publisher.builder();

                siteBuilder.publisher(publisherBuilder.id(accountId).build());
            }
            if (shouldSetExtAmp) {
                final ObjectNode data = siteExt != null ? siteExt.getData() : null;
                siteBuilder.ext(ExtSite.of(1, data));
            }
            return fpdSiteNode == null ? siteBuilder.build() : fpdResolver.resolveSite(siteBuilder.build(),
                    convertSiteObject(fpdSiteNode));
        }
        return null;
    }

    private static String canonicalUrl(HttpServerRequest request) {
        try {
            return HttpUtil.decodeUrl(request.getParam(CURL_REQUEST_PARAM));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Imp overrideImp(Imp imp, HttpServerRequest request, ObjectNode targetingNode) {
        final String tagId = request.getParam(SLOT_REQUEST_PARAM);
        final Banner banner = imp.getBanner();
        final List<Format> overwrittenFormats = banner != null
                ? createOverrideBannerFormats(request, banner.getFormat())
                : null;
        if (StringUtils.isNotBlank(tagId) || CollectionUtils.isNotEmpty(overwrittenFormats) || targetingNode != null) {
            return imp.toBuilder()
                    .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                    .banner(overrideBanner(imp.getBanner(), overwrittenFormats))
                    .ext(fpdResolver.resolveImpExt(imp.getExt(), targetingNode))
                    .build();
        }
        return null;
    }

    /**
     * Creates formats from request parameters to override origin amp banner formats.
     */
    private static List<Format> createOverrideBannerFormats(HttpServerRequest request, List<Format> formats) {
        final int overrideWidth = parseIntParamOrZero(request, OW_REQUEST_PARAM);
        final int width = parseIntParamOrZero(request, W_REQUEST_PARAM);
        final int overrideHeight = parseIntParamOrZero(request, OH_REQUEST_PARAM);
        final int height = parseIntParamOrZero(request, H_REQUEST_PARAM);
        final String multiSizeParam = request.getParam(MS_REQUEST_PARAM);

        final List<Format> paramsFormats = createFormatsFromParams(overrideWidth, width, overrideHeight, height,
                multiSizeParam);

        return CollectionUtils.isNotEmpty(paramsFormats)
                ? paramsFormats
                : updateFormatsFromParams(formats, width, height);
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

    /**
     * Create new formats from request parameters.
     */
    private static List<Format> createFormatsFromParams(Integer overrideWidth, Integer width, Integer overrideHeight,
                                                        Integer height, String multiSizeParam) {
        final List<Format> formats = new ArrayList<>();

        if (overrideWidth != 0 && overrideHeight != 0) {
            formats.add(Format.builder().w(overrideWidth).h(overrideHeight).build());
        } else if (overrideWidth != 0 && height != 0) {
            formats.add(Format.builder().w(overrideWidth).h(height).build());
        } else if (width != 0 && overrideHeight != 0) {
            formats.add(Format.builder().w(width).h(overrideHeight).build());
        } else if (width != 0 && height != 0) {
            formats.add(Format.builder().w(width).h(height).build());
        }

        // Append formats from multi-size param if exist
        final List<Format> multiSizeFormats = StringUtils.isNotBlank(multiSizeParam)
                ? parseMultiSizeParam(multiSizeParam)
                : Collections.emptyList();
        if (!multiSizeFormats.isEmpty()) {
            formats.addAll(multiSizeFormats);
        }

        return formats;
    }

    /**
     * Updates origin amp banner formats from parameters.
     */
    private static List<Format> updateFormatsFromParams(List<Format> formats, Integer width, Integer height) {
        final List<Format> updatedFormats;
        if (width != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(width).h(format.getH()).build())
                    .collect(Collectors.toList());
        } else if (height != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(format.getW()).h(height).build())
                    .collect(Collectors.toList());
        } else {
            updatedFormats = Collections.emptyList();
        }
        return updatedFormats;
    }

    private static Banner overrideBanner(Banner banner, List<Format> formats) {
        return banner != null && CollectionUtils.isNotEmpty(formats)
                ? banner.toBuilder().format(formats).build()
                : banner;
    }

    private static Long overrideTimeout(Long tmax, HttpServerRequest request) {
        final String timeoutQueryParam = request.getParam(TIMEOUT_REQUEST_PARAM);
        if (timeoutQueryParam == null) {
            return null;
        }

        final long timeout;
        try {
            timeout = Long.parseLong(timeoutQueryParam);
        } catch (NumberFormatException e) {
            return null;
        }

        return timeout > 0 && !Objects.equals(timeout, tmax) ? timeout : null;
    }

    private User overrideUser(User user, String gdprConsent, Targeting targeting) {
        final ObjectNode fpdUserNode = targeting.getUser();
        if (StringUtils.isBlank(gdprConsent) && fpdUserNode == null) {
            return null;
        }

        final boolean hasUser = user != null;
        final ExtUser extUser = hasUser ? user.getExt() : null;

        final ExtUser.ExtUserBuilder extUserBuilder = extUser != null
                ? extUser.toBuilder()
                : ExtUser.builder();

        if (StringUtils.isNotBlank(gdprConsent)) {
            extUserBuilder.consent(gdprConsent);
        }
        final User.UserBuilder userBuilder = hasUser ? user.toBuilder() : User.builder();
        final User gdprUpdatedUser = userBuilder.ext(extUserBuilder.build()).build();
        return fpdUserNode == null
                ? gdprUpdatedUser
                : fpdResolver.resolveUser(gdprUpdatedUser, convertUser(fpdUserNode));

    }

    /**
     * Extracts {@link ExtUser} from bidrequest.user.ext {@link ObjectNode}.
     */
    private ExtUser extractExtUser(ObjectNode extUserNode) {
        try {
            return mapper.mapper().treeToValue(extUserNode, ExtUser.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()));
        }
    }

    /**
     * Converts {@link User} from {@link ObjectNode} representation.
     */
    private User convertUser(ObjectNode fpdUserNode) {
        try {
            return mapper.mapper().treeToValue(fpdUserNode, User.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding targeting user: %s", e.getMessage()));
        }
    }

    /**
     * Converts {@link Site} object from {@link ObjectNode} representation to defined in parameters class.
     */
    private Site convertSiteObject(ObjectNode siteNode) {
        try {
            return mapper.mapper().treeToValue(siteNode, Site.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding targeting site: %s", e.getMessage()));
        }
    }

    private Regs overrideRegs(Regs regs, String ccpaConsent) {
        if (StringUtils.isBlank(ccpaConsent)) {
            return null;
        }

        Integer coppa = null;
        Integer gdpr = null;
        if (regs != null) {
            coppa = regs.getCoppa();
            gdpr = regs.getExt().getGdpr();
        }

        return Regs.of(coppa, ExtRegs.of(gdpr, ccpaConsent));
    }

    private ObjectNode overrideExtBidRequest(ObjectNode extNode, Targeting targeting) {
        final List<String> targetingBidders = targeting.getBidders();
        if (CollectionUtils.isEmpty(targetingBidders)) {
            return null;
        }
        return fpdResolver.resolveBidRequestExt(extNode, targetingBidders);
    }

    private static List<Format> parseMultiSizeParam(String ms) {
        final String[] formatStrings = ms.split(",", NO_LIMIT_SPLIT_MODE);
        final List<Format> formats = new ArrayList<>();
        for (String format : formatStrings) {
            final String[] widthHeight = format.split("x", NO_LIMIT_SPLIT_MODE);
            if (widthHeight.length != 2) {
                return Collections.emptyList();
            }

            final Integer width = parseIntOrZero(widthHeight[0]);
            final Integer height = parseIntOrZero(widthHeight[1]);

            if (width == 0 && height == 0) {
                return Collections.emptyList();
            }

            formats.add(Format.builder()
                    .w(width)
                    .h(height)
                    .build());
        }
        return formats;
    }

    /**
     * Creates updated bidrequest.ext {@link ObjectNode}.
     */
    private ExtRequest extRequest(BidRequest bidRequest, ExtRequestPrebid prebid,
                                  boolean setDefaultTargeting, boolean setDefaultCache,
                                  Integer updatedDebug) {
        final ExtRequest result;
        if (setDefaultTargeting || setDefaultCache || updatedDebug != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            if (setDefaultTargeting) {
                prebidBuilder.targeting(createTargetingWithDefaults(prebid));
            }
            if (setDefaultCache) {
                prebidBuilder.cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null),
                        ExtRequestPrebidCacheVastxml.of(null, null), null));
            }
            if (updatedDebug != null) {
                prebidBuilder.debug(updatedDebug);
            }

            result = ExtRequest.of(prebidBuilder.build());
        } else {
            result = bidRequest.getExt();
        }
        return result;
    }

    /**
     * Creates updated with default values bidrequest.ext.targeting {@link ExtRequestTargeting} if at least one of it's
     * child properties is missed or entire targeting does not exist.
     */
    private ExtRequestTargeting createTargetingWithDefaults(ExtRequestPrebid prebid) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final boolean isTargetingNull = targeting == null;

        final JsonNode priceGranularityNode = isTargetingNull ? null : targeting.getPricegranularity();
        final boolean isPriceGranularityNull = priceGranularityNode == null || priceGranularityNode.isNull();
        final JsonNode outgoingPriceGranularityNode = isPriceGranularityNull
                ? mapper.mapper().valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT))
                : priceGranularityNode;

        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = isTargetingNull
                ? null : targeting.getMediatypepricegranularity();

        final boolean includeWinners = isTargetingNull || targeting.getIncludewinners() == null
                || targeting.getIncludewinners();

        final boolean includeBidderKeys = isTargetingNull || targeting.getIncludebidderkeys() == null
                || targeting.getIncludebidderkeys();

        return ExtRequestTargeting.builder()
                .pricegranularity(outgoingPriceGranularityNode)
                .mediatypepricegranularity(mediaTypePriceGranularity)
                .includewinners(includeWinners)
                .includebidderkeys(includeBidderKeys)
                .build();
    }
}
