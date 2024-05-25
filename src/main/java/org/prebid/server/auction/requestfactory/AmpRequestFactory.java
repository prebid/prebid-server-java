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
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AmpGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.ConsentType;
import org.prebid.server.auction.privacy.contextfactory.AmpPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAmp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AmpRequestFactory {

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
    private static final String CONSENT_STRING_PARAM = "consent_string";
    private static final String GDPR_APPLIES_PARAM = "gdpr_applies";
    private static final String CONSENT_TYPE_PARAM = "consent_type";
    private static final String ADDTL_CONSENT_PARAM = "addtl_consent";
    private static final String GPP_SID_PARAM = "gpp_sid";

    private static final int NO_LIMIT_SPLIT_MODE = -1;
    private static final String ENDPOINT = Endpoint.openrtb2_amp.value();

    private final Ortb2RequestFactory ortb2RequestFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final AmpGppService gppService;
    private final OrtbTypesResolver ortbTypesResolver;
    private final ImplicitParametersExtractor implicitParametersExtractor;
    private final Ortb2ImplicitParametersResolver paramsResolver;
    private final FpdResolver fpdResolver;
    private final AmpPrivacyContextFactory ampPrivacyContextFactory;
    private final DebugResolver debugResolver;
    private final JacksonMapper mapper;
    private final GeoLocationServiceWrapper geoLocationServiceWrapper;

    public AmpRequestFactory(Ortb2RequestFactory ortb2RequestFactory,
                             StoredRequestProcessor storedRequestProcessor,
                             BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                             AmpGppService gppService,
                             OrtbTypesResolver ortbTypesResolver,
                             ImplicitParametersExtractor implicitParametersExtractor,
                             Ortb2ImplicitParametersResolver paramsResolver,
                             FpdResolver fpdResolver,
                             AmpPrivacyContextFactory ampPrivacyContextFactory,
                             DebugResolver debugResolver,
                             JacksonMapper mapper,
                             GeoLocationServiceWrapper geoLocationServiceWrapper) {

        this.ortb2RequestFactory = Objects.requireNonNull(ortb2RequestFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.gppService = Objects.requireNonNull(gppService);
        this.ortbTypesResolver = Objects.requireNonNull(ortbTypesResolver);
        this.implicitParametersExtractor = Objects.requireNonNull(implicitParametersExtractor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.ampPrivacyContextFactory = Objects.requireNonNull(ampPrivacyContextFactory);
        this.mapper = Objects.requireNonNull(mapper);
        this.geoLocationServiceWrapper = Objects.requireNonNull(geoLocationServiceWrapper);
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final String body = routingContext.getBodyAsString();

        final AuctionContext initialAuctionContext = ortb2RequestFactory.createAuctionContext(
                Endpoint.openrtb2_amp, MetricName.amp);

        return ortb2RequestFactory.executeEntrypointHooks(routingContext, body, initialAuctionContext)
                .compose(httpRequest -> parseBidRequest(initialAuctionContext, httpRequest)

                        .map(bidRequest -> ortb2RequestFactory.enrichAuctionContext(
                                initialAuctionContext, httpRequest, bidRequest, startTime)))

                .compose(auctionContext -> ortb2RequestFactory.fetchAccount(auctionContext)
                        .map(auctionContext::with))

                .map(auctionContext -> auctionContext.with(debugResolver.debugContextFrom(auctionContext)))

                .compose(auctionContext -> geoLocationServiceWrapper.lookup(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithGeolocationData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> gppService.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.activityInfrastructureFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> updateBidRequest(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ampPrivacyContextFactory.contextFrom(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(auctionContext)
                        .map(auctionContext::with))

                .compose(auctionContext -> ortb2RequestFactory.executeProcessedAuctionRequestHooks(auctionContext)
                        .map(auctionContext::with))

                .map(ortb2RequestFactory::enrichWithPriceFloors)

                .map(ortb2RequestFactory::updateTimeout)

                .recover(ortb2RequestFactory::restoreResultFromRejection);
    }

    /**
     * Creates {@link BidRequest} and sets properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> parseBidRequest(AuctionContext auctionContext, HttpRequestContext httpRequest) {
        final String tagId = httpRequest.getQueryParams().get(TAG_ID_REQUEST_PARAM);
        if (StringUtils.isBlank(tagId)) {
            return Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
        }

        final ConsentParam consentParam = consentParamFromQueryStringParams(httpRequest);
        final List<String> consentParamValidationErrors = validateConsentParam(consentParam);
        auctionContext.getPrebidErrors().addAll(consentParamValidationErrors);

        final String addtlConsent = addtlConsentFromQueryStringParams(httpRequest);
        final Integer gdpr = gdprFromQueryStringParams(httpRequest);
        final GppSidExtraction gppSidExtraction = gppSidFromQueryStringParams(httpRequest);
        final String gpc = implicitParametersExtractor.gpcFrom(httpRequest);
        final Integer debug = debugFromQueryStringParam(httpRequest);
        final Long timeout = timeoutFromQueryString(httpRequest);

        final BidRequest bidRequest = BidRequest.builder()
                .site(createSite(httpRequest))
                .user(createUser(consentParam, addtlConsent))
                .regs(createRegs(consentParam, gppSidExtraction, gdpr, gpc))
                .test(debug)
                .tmax(timeout)
                .ext(createExt(httpRequest, tagId, debug))
                .build();

        return Future.succeededFuture(bidRequest);
    }

    private static ConsentParam consentParamFromQueryStringParams(HttpRequestContext httpRequest) {
        final ConsentType specifiedConsentType = ConsentType.from(httpRequest.getQueryParams().get(CONSENT_TYPE_PARAM));
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();

        final String consentStringParam = queryParams.get(CONSENT_STRING_PARAM);
        final String gdprConsentParam = queryParams.get(GDPR_CONSENT_PARAM);

        return StringUtils.isNotBlank(consentStringParam)
                ? toConsentParam(consentStringParam, CONSENT_STRING_PARAM, specifiedConsentType)
                : toConsentParam(gdprConsentParam, GDPR_CONSENT_PARAM, specifiedConsentType);
    }

    private static ConsentParam toConsentParam(String consent, String fromParam, ConsentType specifiedConsentType) {
        return ConsentParam.of(
                consent,
                fromParam,
                specifiedConsentType,
                TcfDefinerService.isConsentStringValid(consent),
                Ccpa.isValid(consent));
    }

    private List<String> validateConsentParam(ConsentParam consentParam) {
        final List<String> errors = new ArrayList<>();

        if (consentParam.getSpecifiedType() == ConsentType.UNKNOWN) {
            errors.add("Invalid consent_type param passed");
        }
        if (!consentParam.isValid() && consentParam.isConsentStringPresent()) {
            errors.add("Amp request parameter " + consentParam.getSourceParam()
                    + " has invalid format: " + consentParam.getConsentString());
        }

        return errors;
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

    private static User createUser(ConsentParam consentParam, String addtlConsent) {
        final String consent = consentParam.isTcfCompatible() ? consentParam.getConsentString() : null;
        if (StringUtils.isAllBlank(addtlConsent, consent)) {
            return null;
        }

        final ConsentedProvidersSettings consentedProvidersSettings = StringUtils.isNotBlank(addtlConsent)
                ? ConsentedProvidersSettings.of(addtlConsent)
                : null;

        final ExtUser extUser = consentedProvidersSettings != null
                ? ExtUser.builder()
                .consentedProvidersSettings(consentedProvidersSettings)
                .build()
                : null;

        return User.builder().consent(consent).ext(extUser).build();
    }

    private static Regs createRegs(ConsentParam consentParam,
                                   GppSidExtraction gppSidExtraction,
                                   Integer gdpr,
                                   String gpc) {

        final String usPrivacy = consentParam.isCcpaCompatible() ? consentParam.getConsentString() : null;

        final boolean isSuccessGppSidExtraction = gppSidExtraction.isSuccessExtraction();
        final List<Integer> gppSid = isSuccessGppSidExtraction ? gppSidExtraction.getGppSid() : null;
        final String gpp = isSuccessGppSidExtraction && consentParam.isGppCompatible()
                ? consentParam.getConsentString()
                : null;

        return gdpr != null || usPrivacy != null || gppSid != null || gpp != null || gpc != null
                ? Regs.builder()
                .gdpr(gdpr)
                .usPrivacy(usPrivacy)
                .gppSid(gppSid)
                .gpp(gpp)
                .ext(gpc != null ? ExtRegs.of(null, null, gpc, null) : null)
                .build()
                : null;
    }

    private static ExtRequest createExt(HttpRequestContext httpRequest, String tagId, Integer debug) {
        return ExtRequest.of(
                ExtRequestPrebid.builder()
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

    private static String addtlConsentFromQueryStringParams(HttpRequestContext httpRequest) {
        return httpRequest.getQueryParams().get(ADDTL_CONSENT_PARAM);
    }

    private static Integer gdprFromQueryStringParams(HttpRequestContext httpRequest) {
        final String gdprAppliesParam = httpRequest.getQueryParams().get(GDPR_APPLIES_PARAM);
        if (StringUtils.equals(gdprAppliesParam, "true")) {
            return 1;
        } else if (StringUtils.equals(gdprAppliesParam, "false")) {
            return 0;
        }

        return null;
    }

    private static GppSidExtraction gppSidFromQueryStringParams(HttpRequestContext httpRequest) {
        final String gppSidParam = httpRequest.getQueryParams().get(GPP_SID_PARAM);

        try {
            final List<Integer> gppSid = StringUtils.isNotBlank(gppSidParam)
                    ? Arrays.stream(gppSidParam.split(","))
                    .map(Integer::valueOf)
                    .toList()
                    : null;

            return GppSidExtraction.success(gppSid);
        } catch (IllegalArgumentException e) {
            return GppSidExtraction.failed();
        }
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
                .map(ortbVersionConversionManager::convertToAuctionSupportedVersion)
                .map(bidRequest -> gppService.updateBidRequest(bidRequest, auctionContext))
                .map(bidRequest -> validateStoredBidRequest(storedRequestId, bidRequest))
                .map(this::fillExplicitParameters)
                .map(bidRequest -> overrideParameters(bidRequest, httpRequest, auctionContext.getPrebidErrors()))
                .map(bidRequest -> paramsResolver.resolve(bidRequest, auctionContext, ENDPOINT, true))
                .compose(resolvedBidRequest -> ortb2RequestFactory.validateRequest(
                        resolvedBidRequest,
                        auctionContext.getHttpRequest(),
                        auctionContext.getDebugWarnings()));
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
                    "data for tag_id='%s' does not define the required imp array.".formatted(tagId));
        }

        final int impSize = imps.size();
        if (impSize > 1) {
            throw new InvalidRequestException(
                    "data for tag_id '%s' includes %d imp elements. Only one is allowed".formatted(tagId, impSize));
        }

        if (bidRequest.getApp() != null) {
            throw new InvalidRequestException("request.app must not exist in AMP stored requests.");
        }

        if (bidRequest.getDooh() != null) {
            throw new InvalidRequestException("request.dooh must not exist in AMP stored requests.");
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

        final BidRequest result;
        if (setSecure
                || setDefaultTargeting
                || setDefaultCache) {

            result = bidRequest.toBuilder()
                    .imp(setSecure ? Collections.singletonList(imps.get(0).toBuilder().secure(1).build()) : imps)
                    .ext(extRequest(
                            bidRequest,
                            setDefaultTargeting,
                            setDefaultCache))
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
        final String referer = implicitParametersExtractor.refererFrom(httpRequest);
        ortbTypesResolver.normalizeTargeting(targetingNode, errors, referer);

        final Site updatedSite = overrideSite(bidRequest.getSite());
        final Imp updatedImp = overrideImp(bidRequest.getImp().get(0), httpRequest, targetingNode);

        if (ObjectUtils.anyNotNull(updatedSite, updatedImp)) {
            return bidRequest.toBuilder()
                    .site(updatedSite != null ? updatedSite : bidRequest.getSite())
                    .imp(updatedImp != null ? Collections.singletonList(updatedImp) : bidRequest.getImp())
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
            throw new InvalidRequestException("Error reading targeting json " + e.getMessage());
        }
    }

    private ObjectNode validateAndGetTargeting(JsonNode jsonNodeTargeting) {
        if (jsonNodeTargeting.isObject()) {
            return (ObjectNode) jsonNodeTargeting;
        } else {
            throw new InvalidRequestException("Error decoding targeting, expected type is `object` but was "
                    + jsonNodeTargeting.getNodeType().name());
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
                    .toList();
        } else if (height != 0) {
            updatedFormats = formats.stream()
                    .map(format -> Format.builder().w(format.getW()).h(height).build())
                    .toList();
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
                                  boolean setDefaultCache) {

        final ExtRequest result;
        if (setDefaultTargeting || setDefaultCache) {
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
        final JsonNode outgoingPriceGranularityNode
                = isPriceGranularityNull
                ? mapper.mapper().valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT))
                : priceGranularityNode;

        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = isTargetingNull
                ? null
                : targeting.getMediatypepricegranularity();

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

    @Value(staticConstructor = "of")
    private static class GppSidExtraction {

        List<Integer> gppSid;

        boolean successExtraction;

        static GppSidExtraction success(List<Integer> gppSid) {
            return GppSidExtraction.of(gppSid, true);
        }

        static GppSidExtraction failed() {
            return GppSidExtraction.of(null, false);
        }
    }

    @Value(staticConstructor = "of")
    private static class ConsentParam {

        String consentString;

        String sourceParam;

        ConsentType specifiedType;

        boolean isTcf;

        boolean isCcpa;

        public boolean isTcfCompatible() {
            final boolean typeSpecifiedAsTcf =
                    specifiedType == ConsentType.TCF_V1 || specifiedType == ConsentType.TCF_V2;

            return (isConsentStringPresent() && typeSpecifiedAsTcf) || isTcf;
        }

        public boolean isCcpaCompatible() {
            return (isConsentStringPresent() && specifiedType == ConsentType.CCPA) || isCcpa;
        }

        public boolean isGppCompatible() {
            return isConsentStringPresent() && specifiedType == ConsentType.GPP;
        }

        public boolean isValid() {
            return isTcfCompatible() || isCcpaCompatible();
        }

        public boolean isConsentStringPresent() {
            return StringUtils.isNotBlank(consentString);
        }
    }
}
