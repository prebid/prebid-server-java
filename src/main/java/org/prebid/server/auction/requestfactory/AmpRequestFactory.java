package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAmp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private static final String AMP_CHANNEL = "amp";
    private static final String ENDPOINT = Endpoint.openrtb2_amp.value();

    private final Ortb2RequestFactory ortb2RequestFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final OrtbTypesResolver ortbTypesResolver;
    private final ImplicitParametersExtractor implicitParametersExtractor;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final FpdResolver fpdResolver;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final TimeoutResolver timeoutResolver;
    private final JacksonMapper mapper;

    public AmpRequestFactory(StoredRequestProcessor storedRequestProcessor,
                             Ortb2RequestFactory ortb2RequestFactory,
                             OrtbTypesResolver ortbTypesResolver,
                             ImplicitParametersExtractor implicitParametersExtractor,
                             Ortb2ImplicitParametersResolver paramsResolver,
                             FpdResolver fpdResolver,
                             PrivacyEnforcementService privacyEnforcementService,
                             TimeoutResolver timeoutResolver,
                             JacksonMapper mapper) {

        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.ortbTypesResolver = Objects.requireNonNull(ortbTypesResolver);
        this.implicitParametersExtractor = Objects.requireNonNull(implicitParametersExtractor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String body = routingContext.getBodyAsString();

        final AuctionContext initialAuctionContext = ortb2RequestFactory.createAuctionContext(
                Endpoint.openrtb2_amp, MetricName.amp);

        return ortb2RequestFactory.executeEntrypointHooks(routingContext, body, initialAuctionContext)
                .compose(httpRequest -> parseBidRequest(httpRequest, initialAuctionContext)
                        .map(bidRequest -> ortb2RequestFactory.enrichAuctionContext(
                                initialAuctionContext, httpRequest, bidRequest, startTime)))

                .compose(auctionContext -> ortb2RequestFactory.fetchAccount(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> updateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> privacyEnforcementService.contextFromBidRequest(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(
                        ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .recover(ortb2RequestFactory::restoreResultFromRejection);
    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> parseBidRequest(HttpRequestContext httpRequest, AuctionContext auctionContext) {
        final String tagId = httpRequest.getQueryParams().get(TAG_ID_REQUEST_PARAM);
        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
        }

        final String consentString = consentStringFromQueryStringParams(httpRequest);
        final Integer debug = debugFromQueryStringParam(httpRequest);
        final Long timeout = timeoutFromQueryString(httpRequest);

        final BidRequest bidRequest = BidRequest.builder()
                .site(createSite(httpRequest))
                .user(createUser(consentString))
                .regs(createRegs(consentString))
                .test(debug)
                .tmax(timeout)
                .ext(createExt(httpRequest, tagId, debug))
                .build();

        validateOriginalBidRequest(bidRequest, consentString, auctionContext);

        return Future.succeededFuture(bidRequest);
    }

    private static Site createSite(HttpRequestContext httpRequest) {
        final String accountId = StringUtils.trimToNull(httpRequest.getQueryParams().get(ACCOUNT_REQUEST_PARAM));
        final String canonicalUrl = StringUtils.trimToNull(canonicalUrl(httpRequest));
        final String domain = StringUtils.trimToNull(HttpUtil.getHostFromUrl(canonicalUrl));

        return !StringUtils.isAllBlank(accountId, canonicalUrl, domain)
                ? Site.builder()
                .publisher(Publisher.builder().id(accountId).build())
                .page(canonicalUrl)
                .domain(domain)
                .build()
                : null;
    }

    private static User createUser(String consentString) {
        return StringUtils.isNotBlank(consentString) && TcfDefinerService.isConsentStringValid(consentString)
                ? User.builder().ext(ExtUser.builder().consent(consentString).build()).build()
                : null;
    }

    private static Regs createRegs(String consentString) {
        return StringUtils.isNotBlank(consentString) && Ccpa.isValid(consentString)
                ? Regs.of(null, ExtRegs.of(null, consentString))
                : null;
    }

    private static ExtRequest createExt(HttpRequestContext httpRequest, String tagId, Integer debug) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of(tagId))
                .amp(ExtRequestPrebidAmp.of(ampDataFromQueryString(httpRequest)))
                .debug(debug)
                .build());
    }

    /**
     * Returns debug flag from request query string if it is equal to either 0 or 1, or null if otherwise.
     */
    private static Integer debugFromQueryStringParam(HttpRequestContext httpRequest) {
        final String debug = httpRequest.getQueryParams().get(DEBUG_REQUEST_PARAM);
        return Objects.equals(debug, "1") ? Integer.valueOf(1) : Objects.equals(debug, "0") ? 0 : null;
    }

    private static String canonicalUrl(HttpRequestContext httpRequest) {
        try {
            return HttpUtil.decodeUrl(httpRequest.getQueryParams().get(CURL_REQUEST_PARAM));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String consentStringFromQueryStringParams(HttpRequestContext httpRequest) {
        final String requestConsentParam = httpRequest.getQueryParams().get(CONSENT_PARAM);
        final String requestGdprConsentParam = httpRequest.getQueryParams().get(GDPR_CONSENT_PARAM);

        return ObjectUtils.firstNonNull(requestConsentParam, requestGdprConsentParam);
    }

    private static Long timeoutFromQueryString(HttpRequestContext httpRequest) {
        final String timeoutQueryParam = httpRequest.getQueryParams().get(TIMEOUT_REQUEST_PARAM);
        if (timeoutQueryParam == null) {
            return null;
        }

        final long timeout;
        try {
            timeout = Long.parseLong(timeoutQueryParam);
        } catch (NumberFormatException e) {
            return null;
        }

        return timeout > 0 ? timeout : null;
    }

    private static Map<String, String> ampDataFromQueryString(HttpRequestContext httpRequest) {
        return httpRequest.getQueryParams().entries().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (value1, value2) -> value1));
    }

    private static void validateOriginalBidRequest(
            BidRequest bidRequest,
            String requestConsentString,
            AuctionContext auctionContext) {

        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String gdprConsentString = extUser != null ? extUser.getConsent() : null;

        final Regs regs = bidRequest.getRegs();
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final String usPrivacy = extRegs != null ? extRegs.getUsPrivacy() : null;

        if (StringUtils.isAllBlank(gdprConsentString, usPrivacy)) {
            final String message = String.format(
                    "Amp request parameter %s or %s have invalid format: %s",
                    CONSENT_PARAM,
                    GDPR_CONSENT_PARAM,
                    requestConsentString);
            logger.debug(message);
            auctionContext.getPrebidErrors().add(message);
        }
    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> updateBidRequest(AuctionContext auctionContext) {
        final BidRequest receivedBidRequest = auctionContext.getBidRequest();
        final String storedRequestId = storedRequestId(receivedBidRequest);
        if (StringUtils.isBlank(storedRequestId)) {
            return Future.failedFuture(
                    new InvalidRequestException("AMP requests require the stored request id in AMP tag_id"));
        }

        final Account account = auctionContext.getAccount();
        final String accountId = account != null ? account.getId() : null;

        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();

        return storedRequestProcessor.processAmpRequest(accountId, storedRequestId, receivedBidRequest)
                .map(bidRequest -> validateStoredBidRequest(storedRequestId, bidRequest))
                .map(this::fillExplicitParameters)
                .map(bidRequest -> overrideParameters(bidRequest, httpRequest, auctionContext.getPrebidErrors()))
                .map(bidRequest -> paramsResolver.resolve(bidRequest, httpRequest, timeoutResolver, ENDPOINT))
                .map(ortb2RequestFactory::validateRequest);
    }

    private static String storedRequestId(BidRequest receivedBidRequest) {
        final ExtRequest requestExt = receivedBidRequest != null ? receivedBidRequest.getExt() : null;
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtStoredRequest storedRequest = prebid != null ? prebid.getStoredrequest() : null;
        return storedRequest != null ? storedRequest.getId() : null;
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

        return bidRequest;
    }

    /**
     * - Updates {@link BidRequest}.ext.prebid.targeting and {@link BidRequest}.ext.prebid.cache.bids with default
     * values if it was not included by user
     * - Updates {@link Imp} security if required to ensure that amp always uses
     * https protocol
     * - Sets {@link BidRequest}.test = 1 if it was passed in {@link RoutingContext}
     * - Updates {@link BidRequest}.ext.prebid.amp.data with all query parameters
     */
    private BidRequest fillExplicitParameters(BidRequest bidRequest) {
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

        final boolean setChannel;

        if (prebid == null) {
            setDefaultTargeting = true;
            setDefaultCache = true;
            setChannel = true;
        } else {
            final ExtRequestTargeting targeting = prebid.getTargeting();
            setDefaultTargeting = targeting == null
                    || targeting.getIncludewinners() == null
                    || targeting.getIncludebidderkeys() == null
                    || targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull();

            final ExtRequestPrebidCache cache = prebid.getCache();
            setDefaultCache = cache == null || cache.equals(ExtRequestPrebidCache.EMPTY);

            setChannel = prebid.getChannel() == null;
        }

        final BidRequest result;
        if (setSecure
                || setDefaultTargeting
                || setDefaultCache
                || setChannel) {

            result = bidRequest.toBuilder()
                    .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                    .ext(extRequest(
                            bidRequest,
                            setDefaultTargeting,
                            setDefaultCache,
                            setChannel))
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    /**
     * Extracts parameters from http request and overrides corresponding attributes in {@link BidRequest}.
     */
    private BidRequest overrideParameters(BidRequest bidRequest, HttpRequestContext httpRequest, List<String> errors) {
        final String requestTargeting = httpRequest.getQueryParams().get(TARGETING_REQUEST_PARAM);
        final ObjectNode targetingNode = readTargeting(requestTargeting);
        ortbTypesResolver.normalizeTargeting(
                targetingNode, errors, implicitParametersExtractor.refererFrom(httpRequest));
        final Targeting targeting = parseTargeting(targetingNode);

        final Site updatedSite = overrideSite(bidRequest.getSite());
        final Imp updatedImp = overrideImp(bidRequest.getImp().get(0), httpRequest, targetingNode);
        final ExtRequest updatedExtBidRequest = overrideExtBidRequest(bidRequest.getExt(), targeting);

        if (ObjectUtils.anyNotNull(updatedSite, updatedImp, updatedExtBidRequest)) {
            return bidRequest.toBuilder()
                    .site(updatedSite != null ? updatedSite : bidRequest.getSite())
                    .imp(updatedImp != null ? Collections.singletonList(updatedImp) : bidRequest.getImp())
                    .ext(updatedExtBidRequest != null ? updatedExtBidRequest : bidRequest.getExt())
                    .build();
        }

        return bidRequest;
    }

    private ObjectNode readTargeting(String jsonTargeting) {
        try {
            final String decodedJsonTargeting = HttpUtil.decodeUrl(jsonTargeting);
            final JsonNode jsonNodeTargeting = decodedJsonTargeting != null
                    ? mapper.mapper().readTree(decodedJsonTargeting)
                    : null;
            return jsonNodeTargeting != null ? validateAndGetTargeting(jsonNodeTargeting) : null;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidRequestException(String.format("Error reading targeting json %s", e.getMessage()));
        }
    }

    private ObjectNode validateAndGetTargeting(JsonNode jsonNodeTargeting) {
        if (jsonNodeTargeting.isObject()) {
            return (ObjectNode) jsonNodeTargeting;
        } else {
            throw new InvalidRequestException(String.format("Error decoding targeting, expected type is `object` "
                    + "but was %s", jsonNodeTargeting.getNodeType().name()));
        }
    }

    private Targeting parseTargeting(ObjectNode targetingNode) {
        try {
            return targetingNode == null
                    ? Targeting.empty()
                    : mapper.mapper().treeToValue(targetingNode, Targeting.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding targeting from url: %s", e.getMessage()));
        }
    }

    private Site overrideSite(Site site) {
        final boolean hasSite = site != null;
        final ExtSite siteExt = hasSite ? site.getExt() : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.getAmp() == null;

        if (shouldSetExtAmp) {
            final Site.SiteBuilder siteBuilder = hasSite ? site.toBuilder() : Site.builder();

            final ObjectNode data = siteExt != null ? siteExt.getData() : null;
            siteBuilder.ext(ExtSite.of(1, data));

            return siteBuilder.build();
        }

        return null;
    }

    private Imp overrideImp(Imp imp, HttpRequestContext httpRequest, ObjectNode targetingNode) {
        final String tagId = httpRequest.getQueryParams().get(SLOT_REQUEST_PARAM);
        final Banner banner = imp.getBanner();
        final List<Format> overwrittenFormats = banner != null
                ? createOverrideBannerFormats(httpRequest, banner.getFormat())
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
    private static List<Format> createOverrideBannerFormats(HttpRequestContext httpRequest, List<Format> formats) {
        final int overrideWidth = parseIntParamOrZero(httpRequest, OW_REQUEST_PARAM);
        final int width = parseIntParamOrZero(httpRequest, W_REQUEST_PARAM);
        final int overrideHeight = parseIntParamOrZero(httpRequest, OH_REQUEST_PARAM);
        final int height = parseIntParamOrZero(httpRequest, H_REQUEST_PARAM);
        final String multiSizeParam = httpRequest.getQueryParams().get(MS_REQUEST_PARAM);

        final List<Format> paramsFormats = createFormatsFromParams(overrideWidth, width, overrideHeight, height,
                multiSizeParam);

        return CollectionUtils.isNotEmpty(paramsFormats)
                ? paramsFormats
                : updateFormatsFromParams(formats, width, height);
    }

    private static Integer parseIntParamOrZero(HttpRequestContext httpRequest, String name) {
        return parseIntOrZero(httpRequest.getQueryParams().get(name));
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

    /**
     * Overrides {@link ExtRequest} with first party data.
     */
    private ExtRequest overrideExtBidRequest(ExtRequest extRequest, Targeting targeting) {
        return fpdResolver.resolveBidRequestExt(extRequest, targeting);
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
    private ExtRequest extRequest(BidRequest bidRequest,
                                  boolean setDefaultTargeting,
                                  boolean setDefaultCache,
                                  boolean setChannel) {

        final ExtRequest result;
        if (setDefaultTargeting || setDefaultCache || setChannel) {
            final ExtRequest requestExt = bidRequest.getExt();
            final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
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
            if (setChannel) {
                prebidBuilder.channel(ExtRequestPrebidChannel.of(AMP_CHANNEL));
            }

            final ExtRequest updatedExt = ExtRequest.of(prebidBuilder.build());
            if (requestExt != null) {
                updatedExt.addProperties(requestExt.getProperties());
            }

            result = updatedExt;
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

        final Boolean includeFormat = !isTargetingNull ? targeting.getIncludeformat() : null;

        return (isTargetingNull ? ExtRequestTargeting.builder() : targeting.toBuilder())
                .pricegranularity(outgoingPriceGranularityNode)
                .mediatypepricegranularity(mediaTypePriceGranularity)
                .includewinners(includeWinners)
                .includebidderkeys(includeBidderKeys)
                .includeformat(includeFormat)
                .build();
    }
}
