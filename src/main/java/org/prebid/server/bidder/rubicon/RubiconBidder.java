package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.request.RubiconAppExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconDeviceExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconExtPrebidBidders;
import org.prebid.server.bidder.rubicon.proto.request.RubiconExtPrebidBiddersBidder;
import org.prebid.server.bidder.rubicon.proto.request.RubiconExtPrebidBiddersBidderDebug;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.request.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconUserExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconVideoExtRp;
import org.prebid.server.bidder.rubicon.proto.response.RubiconBidResponse;
import org.prebid.server.bidder.rubicon.proto.response.RubiconSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContextDataAdserver;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUidExt;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubiconDebug;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RubiconBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RubiconBidder.class);
    private static final ConditionalLogger MISSING_VIDEO_SIZE_LOGGER =
            new ConditionalLogger("missing_video_size", logger);

    private static final String TK_XINT_QUERY_PARAMETER = "tk_xint";
    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private static final String ADSERVER_EID = "adserver.org";
    private static final String LIVEINTENT_EID = "liveintent.com";
    private static final String LIVERAMP_EID = "liveramp.com";
    private static final String SOURCE_RUBICON = "rubiconproject.com";

    private static final String FPD_GPID_FIELD = "gpid";
    private static final String FPD_SECTIONCAT_FIELD = "sectioncat";
    private static final String FPD_PAGECAT_FIELD = "pagecat";
    private static final String FPD_PAGE_FIELD = "page";
    private static final String FPD_REF_FIELD = "ref";
    private static final String FPD_SEARCH_FIELD = "search";
    private static final String FPD_CONTEXT_FIELD = "context";
    private static final String FPD_DATA_FIELD = "data";
    private static final String FPD_DATA_PBADSLOT_FIELD = "pbadslot";
    private static final String FPD_ADSERVER_FIELD = "adserver";
    private static final String FPD_ADSERVER_NAME_GAM = "gam";
    private static final String FPD_KEYWORDS_FIELD = "keywords";
    private static final String DFP_ADUNIT_CODE_FIELD = "dfp_ad_unit_code";
    private static final String PREBID_EXT = "prebid";

    private static final String PPUID_STYPE = "ppuid";
    private static final String OTHER_STYPE = "other";
    private static final String SHA256EMAIL_STYPE = "sha256email";
    private static final String DMP_STYPE = "dmp";
    private static final String XAPI_CURRENCY = "USD";

    private static final Set<Integer> USER_SEGTAXES = Set.of(4);
    private static final Set<Integer> SITE_SEGTAXES = Set.of(1, 2, 5, 6);

    private static final Set<String> STYPE_TO_REMOVE = new HashSet<>(Arrays.asList(PPUID_STYPE, SHA256EMAIL_STYPE,
            DMP_STYPE));
    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int PORTRAIT_MOBILE_SIZE_ID = 67;
    private static final int LANDSCAPE_MOBILE_SIZE_ID = 101;

    private final String endpointUrl;
    private final Set<String> supportedVendors;
    private final boolean generateBidId;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    private final MultiMap headers;

    public RubiconBidder(String endpoint,
                         String xapiUsername,
                         String xapiPassword,
                         List<String> supportedVendors,
                         boolean generateBidId,
                         CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.supportedVendors = Set.copyOf(Objects.requireNonNull(supportedVendors));
        this.generateBidId = generateBidId;
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);

        headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> imps = extractValidImps(bidRequest, errors);
        if (CollectionUtils.isEmpty(imps)) {
            errors.add(BidderError.badInput("There are no valid impressions to create bid request to rubicon bidder"));
            return Result.withErrors(errors);
        }

        final Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToImpExt = parseRubiconImpExts(imps, errors);
        final String impLanguage = firstImpExtLanguage(impToImpExt.values());
        final String uri = makeUri(bidRequest);

        for (Map.Entry<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToExt : impToImpExt.entrySet()) {
            try {
                final Imp imp = impToExt.getKey();
                final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = impToExt.getValue();
                final BidRequest singleImpRequest = createSingleRequest(
                        imp, ext.getPrebid(), ext.getBidder(), bidRequest, impLanguage, errors);
                if (hasDeals(imp)) {
                    httpRequests.addAll(createDealsRequests(singleImpRequest, uri));
                } else {
                    httpRequests.add(createHttpRequest(singleImpRequest, uri));
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final RubiconBidResponse bidResponse =
                    mapper.decodeValue(httpCall.getResponse().getBody(), RubiconBidResponse.class);
            return Result.of(extractBids(bidRequest, httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode extBidBidder) {
        final RubiconTargetingExt rubiconTargetingExt;
        try {
            rubiconTargetingExt = mapper.mapper().convertValue(extBidBidder, RubiconTargetingExt.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Error adding rubicon specific targeting to amp response", e);
            return Collections.emptyMap();
        }

        final RubiconTargetingExtRp rp = rubiconTargetingExt.getRp();
        final List<RubiconTargeting> targetings = rp != null ? rp.getTargeting() : null;
        return targetings != null
                ? targetings.stream()
                .filter(targeting -> !CollectionUtils.isEmpty(targeting.getValues()))
                .collect(Collectors.toMap(RubiconTargeting::getKey, targeting -> targeting.getValues().get(0)))
                : Collections.emptyMap();
    }

    private List<Imp> extractValidImps(BidRequest bidRequest, List<BidderError> errors) {
        final Map<Boolean, List<Imp>> isValidToImps = bidRequest.getImp().stream()
                .collect(Collectors.groupingBy(RubiconBidder::isValidType));

        isValidToImps.getOrDefault(false, Collections.emptyList()).stream()
                .map(this::impTypeErrorMessage)
                .forEach(errors::add);

        return isValidToImps.getOrDefault(true, Collections.emptyList());
    }

    private static boolean isValidType(Imp imp) {
        return imp.getVideo() != null || imp.getBanner() != null;
    }

    private BidderError impTypeErrorMessage(Imp imp) {
        final BidType type = resolveExpectedBidType(imp);
        return BidderError.badInput(String.format("Impression with id %s rejected with invalid type `%s`."
                + " Allowed types are banner and video.", imp.getId(), type != null ? type.name() : "unknown"));
    }

    private static BidType resolveExpectedBidType(Imp imp) {
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        if (imp.getAudio() != null) {
            return BidType.audio;
        }
        if (imp.getXNative() != null) {
            return BidType.xNative;
        }
        return null;
    }

    private static MultiMap headers(String xapiUsername, String xapiPassword) {
        return HttpUtil.headers()
                .add(HttpUtil.AUTHORIZATION_HEADER, authHeader(xapiUsername, xapiPassword))
                .add(HttpUtil.USER_AGENT_HEADER, PREBID_SERVER_USER_AGENT);
    }

    private static String authHeader(String xapiUsername, String xapiPassword) {
        return "Basic " + Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    private Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> parseRubiconImpExts(
            List<Imp> imps, List<BidderError> errors) {

        final Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToImpExt = new HashMap<>();
        for (final Imp imp : imps) {
            try {
                final ExtPrebid<ExtImpPrebid, ExtImpRubicon> rubiconImpExt = parseRubiconExt(imp);
                impToImpExt.put(imp, rubiconImpExt);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return impToImpExt;
    }

    private ExtPrebid<ExtImpPrebid, ExtImpRubicon> parseRubiconExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static String firstImpExtLanguage(Collection<ExtPrebid<ExtImpPrebid, ExtImpRubicon>> rubiconImpExts) {
        return rubiconImpExts.stream()
                .filter(Objects::nonNull)
                .map(ExtPrebid::getBidder)
                .map(ExtImpRubicon::getVideo)
                .filter(Objects::nonNull)
                .map(RubiconVideoParams::getLanguage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private BidRequest createSingleRequest(Imp imp,
                                           ExtImpPrebid extImpPrebid,
                                           ExtImpRubicon extImpRubicon,
                                           BidRequest bidRequest,
                                           String impLanguage,
                                           List<BidderError> errors) {

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(makeImp(imp, extImpPrebid, extImpRubicon, bidRequest, errors)))
                .user(makeUser(bidRequest.getUser(), extImpRubicon))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(bidRequest.getSite(), impLanguage, extImpRubicon))
                .app(makeApp(bidRequest.getApp(), extImpRubicon))
                .source(makeSource(bidRequest.getSource(), extImpRubicon.getPchain()))
                .cur(null) // suppress currencies
                .ext(null) // suppress ext
                .build();
    }

    private String makeUri(BidRequest bidRequest) {
        final String tkXint = tkXintValue(bidRequest);
        if (StringUtils.isNotBlank(tkXint)) {
            try {
                return new URIBuilder(endpointUrl)
                        .setParameter(TK_XINT_QUERY_PARAMETER, tkXint)
                        .build().toString();
            } catch (URISyntaxException e) {
                throw new PreBidException(String.format("Cant add the tk_xint value for url: %s", tkXint), e);
            }
        }
        return endpointUrl;
    }

    private String tkXintValue(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebidExt = requestExt != null ? requestExt.getPrebid() : null;
        final String integration = prebidExt != null ? prebidExt.getIntegration() : null;

        return StringUtils.stripToNull(integration);
    }

    private RubiconExtPrebidBiddersBidder extPrebidBiddersRubicon(ExtRequest extRequest) {
        final ExtRequestPrebid prebid = extRequest == null ? null : extRequest.getPrebid();
        final ObjectNode biddersNode = prebid == null ? null : prebid.getBidders();
        if (biddersNode != null) {
            try {
                final RubiconExtPrebidBidders bidders = mapper.mapper().convertValue(biddersNode,
                        RubiconExtPrebidBidders.class);
                return bidders == null ? null : bidders.getBidder();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private Imp makeImp(Imp imp,
                        ExtImpPrebid extImpPrebid,
                        ExtImpRubicon extImpRubicon,
                        BidRequest bidRequest,
                        List<BidderError> errors) {

        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        final ExtRequest extRequest = bidRequest.getExt();

        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(mapper.mapper().valueToTree(makeImpExt(imp, extImpRubicon, site, app, extRequest)));

        final BigDecimal resolvedBidFloor = resolveBidFloor(imp, bidRequest, errors);
        if (resolvedBidFloor != null) {
            builder
                    .bidfloorcur(XAPI_CURRENCY)
                    .bidfloor(resolvedBidFloor);
        }

        if (isVideo(imp)) {
            builder
                    .banner(null)
                    .video(makeVideo(imp, extImpRubicon.getVideo(), extImpPrebid, referer(site)));
        } else {
            builder
                    .banner(makeBanner(imp, overriddenSizes(extImpRubicon)))
                    .video(null);
        }

        return builder.build();
    }

    private List<Metric> makeMetrics(Imp imp) {
        final List<Metric> metrics = imp.getMetric();

        if (metrics == null) {
            return null;
        }

        final List<Metric> modifiedMetrics = new ArrayList<>();
        for (Metric metric : metrics) {
            if (isMetricSupported(metric)) {
                modifiedMetrics.add(metric.toBuilder().vendor("seller-declared").build());
            } else {
                modifiedMetrics.add(metric);
            }
        }

        return modifiedMetrics;
    }

    private boolean isMetricSupported(Metric metric) {
        return supportedVendors.contains(metric.getVendor()) && Objects.equals(metric.getType(), "viewability");
    }

    private BigDecimal resolveBidFloor(Imp imp, BidRequest bidRequest, List<BidderError> errors) {
        final BigDecimal resolvedBidFloorPrice = resolveBidFloorPrice(imp);
        if (resolvedBidFloorPrice == null) {
            return null;
        }

        final String resolvedBidFloorCurrency = resolveBidFloorCurrency(imp, bidRequest, errors);
        return ObjectUtils.notEqual(resolvedBidFloorCurrency, XAPI_CURRENCY)
                ? convertBidFloorCurrency(resolvedBidFloorPrice, resolvedBidFloorCurrency, imp, bidRequest)
                : null;
    }

    private static BigDecimal resolveBidFloorPrice(Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        return BidderUtil.isValidPrice(bidFloor) ? bidFloor : null;
    }

    private static String resolveBidFloorCurrency(Imp imp, BidRequest bidRequest, List<BidderError> errors) {
        final String bidFloorCurrency = imp.getBidfloorcur();
        if (StringUtils.isBlank(bidFloorCurrency)) {
            if (isDebugEnabled(bidRequest)) {
                errors.add(BidderError.badInput(String.format("Imp `%s` floor provided with no currency, assuming %s",
                        imp.getId(), XAPI_CURRENCY)));
            }
            return XAPI_CURRENCY;
        }
        return bidFloorCurrency;
    }

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtRequest}.
     */
    private static boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    private BigDecimal convertBidFloorCurrency(BigDecimal bidFloor,
                                               String bidFloorCurrency,
                                               Imp imp,
                                               BidRequest bidRequest) {
        try {
            return currencyConversionService.convertCurrency(bidFloor, bidRequest, bidFloorCurrency, XAPI_CURRENCY);
        } catch (PreBidException e) {
            throw new PreBidException(String.format(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s` with a reason: %s",
                    bidFloorCurrency, XAPI_CURRENCY, imp.getId(), e.getMessage()));
        }
    }

    private RubiconImpExt makeImpExt(Imp imp,
                                     ExtImpRubicon rubiconImpExt,
                                     Site site,
                                     App app,
                                     ExtRequest extRequest) {

        final ExtImpContext context = extImpContext(imp);
        return RubiconImpExt.of(
                RubiconImpExtRp.of(
                        rubiconImpExt.getZoneId(),
                        makeTarget(imp, rubiconImpExt, site, app, context),
                        RubiconImpExtRpTrack.of("", "")),
                mapVendorsNamesToUrls(imp.getMetric()),
                getMaxBids(extRequest),
                getGpid(imp.getExt()));
    }

    private JsonNode makeTarget(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app, ExtImpContext context) {
        final ObjectNode result = mapper.mapper().createObjectNode();

        populateFirstPartyDataAttributes(rubiconImpExt.getInventory(), result);

        mergeFirstPartyDataFromSite(site, result);
        mergeFirstPartyDataFromApp(app, result);
        mergeFirstPartyDataFromImp(imp, rubiconImpExt, context, result);

        return result.size() > 0 ? result : null;
    }

    private ExtImpContext extImpContext(Imp imp) {
        final JsonNode context = imp.getExt().get(FPD_CONTEXT_FIELD);
        if (context == null || context.isNull()) {
            return null;
        }
        try {
            return mapper.mapper().convertValue(context, ExtImpContext.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private void mergeFirstPartyDataFromSite(Site site, ObjectNode result) {
        // merge OPENRTB.site.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
        final ExtSite siteExt = site != null ? site.getExt() : null;
        if (siteExt != null) {
            populateFirstPartyDataAttributes(siteExt.getData(), result);
        }

        // merge OPENRTB.site.sectioncat to every impression XAPI.imp[].ext.rp.target.sectioncat
        mergeCollectionAttributeIntoArray(result, site, Site::getSectioncat, FPD_SECTIONCAT_FIELD);
        // merge OPENRTB.site.pagecat to every impression XAPI.imp[].ext.rp.target.pagecat
        mergeCollectionAttributeIntoArray(result, site, Site::getPagecat, FPD_PAGECAT_FIELD);
        // merge OPENRTB.site.page to every impression XAPI.imp[].ext.rp.target.page
        mergeStringAttributeIntoArray(result, site, Site::getPage, FPD_PAGE_FIELD);
        // merge OPENRTB.site.ref to every impression XAPI.imp[].ext.rp.target.ref
        mergeStringAttributeIntoArray(result, site, Site::getRef, FPD_REF_FIELD);
        // merge OPENRTB.site.search to every impression XAPI.imp[].ext.rp.target.search
        mergeStringAttributeIntoArray(result, site, Site::getSearch, FPD_SEARCH_FIELD);
    }

    private void mergeFirstPartyDataFromApp(App app, ObjectNode result) {
        // merge OPENRTB.app.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
        final ExtApp appExt = app != null ? app.getExt() : null;
        if (appExt != null) {
            populateFirstPartyDataAttributes(appExt.getData(), result);
        }

        // merge OPENRTB.app.sectioncat to every impression XAPI.imp[].ext.rp.target.sectioncat
        mergeCollectionAttributeIntoArray(result, app, App::getSectioncat, FPD_SECTIONCAT_FIELD);
        // merge OPENRTB.app.pagecat to every impression XAPI.imp[].ext.rp.target.pagecat
        mergeCollectionAttributeIntoArray(result, app, App::getPagecat, FPD_PAGECAT_FIELD);
    }

    private void mergeFirstPartyDataFromImp(Imp imp,
                                            ExtImpRubicon rubiconImpExt,
                                            ExtImpContext context,
                                            ObjectNode result) {

        mergeFirstPartyDataFromData(imp, context, result);
        mergeFirstPartyDataKeywords(imp, context, result);
        // merge OPENRTB.imp[].ext.rubicon.keywords to XAPI.imp[].ext.rp.target.keywords
        mergeCollectionAttributeIntoArray(result, rubiconImpExt, ExtImpRubicon::getKeywords, FPD_KEYWORDS_FIELD);
        // merge OPENRTB.imp[].ext.context.search to XAPI.imp[].ext.rp.target.search
        mergeStringAttributeIntoArray(
                result,
                context,
                extContext -> getTextValueFromNode(extContext.getProperty(FPD_SEARCH_FIELD)),
                FPD_SEARCH_FIELD);
        // merge OPENRTB.imp[].ext.data.search to XAPI.imp[].ext.rp.target.search
        mergeStringAttributeIntoArray(
                result,
                imp.getExt().get(FPD_DATA_FIELD),
                node -> getTextValueFromNodeByPath(node, FPD_SEARCH_FIELD),
                FPD_SEARCH_FIELD);
    }

    private void mergeFirstPartyDataFromData(Imp imp, ExtImpContext context, ObjectNode result) {
        final ObjectNode contextDataNode = toObjectNode(
                ObjectUtil.getIfNotNull(context, ExtImpContext::getData));
        // merge OPENRTB.imp[].ext.context.data.* to XAPI.imp[].ext.rp.target.*
        populateFirstPartyDataAttributes(contextDataNode, result);

        final ObjectNode dataNode = toObjectNode(imp.getExt().get(FPD_DATA_FIELD));
        // merge OPENRTB.imp[].ext.data.* to XAPI.imp[].ext.rp.target.*
        populateFirstPartyDataAttributes(dataNode, result);

        // override XAPI.imp[].ext.rp.target.* with OPENRTB.imp[].ext.data.*
        overrideFirstPartyDataAttributes(contextDataNode, dataNode, result);
    }

    private void overrideFirstPartyDataAttributes(ObjectNode contextDataNode, ObjectNode dataNode, ObjectNode result) {
        final JsonNode pbadslotNode = dataNode.get(FPD_DATA_PBADSLOT_FIELD);
        if (pbadslotNode != null && pbadslotNode.isTextual()) {
            // copy imp[].ext.data.pbadslot to XAPI.imp[].ext.rp.target.pbadslot
            result.set(FPD_DATA_PBADSLOT_FIELD, pbadslotNode);
        } else {
            // copy adserver.adslot value to XAPI field imp[].ext.rp.target.dfp_ad_unit_code
            final String resolvedDfpAdUnitCode = getAdSlot(contextDataNode, dataNode);
            if (resolvedDfpAdUnitCode != null) {
                result.set(DFP_ADUNIT_CODE_FIELD, TextNode.valueOf(resolvedDfpAdUnitCode));
            }
        }
    }

    private void mergeFirstPartyDataKeywords(Imp imp, ExtImpContext context, ObjectNode result) {
        // merge OPENRTB.imp[].ext.context.keywords to XAPI.imp[].ext.rp.target.keywords
        final JsonNode keywordsNode = context != null ? context.getProperty("keywords") : null;
        final String keywords = getTextValueFromNode(keywordsNode);
        if (StringUtils.isNotBlank(keywords)) {
            mergeIntoArray(result, FPD_KEYWORDS_FIELD, keywords.split(","));
        }

        // merge OPENRTB.imp[].ext.data.keywords to XAPI.imp[].ext.rp.target.keywords
        final String dataKeywords = getTextValueFromNodeByPath(imp.getExt().get(FPD_DATA_FIELD), FPD_KEYWORDS_FIELD);
        if (StringUtils.isNotBlank(dataKeywords)) {
            mergeIntoArray(result, FPD_KEYWORDS_FIELD, dataKeywords.split(","));
        }
    }

    private <S, T extends Collection<? extends String>> void mergeCollectionAttributeIntoArray(
            ObjectNode result, S source, Function<S, T> getter, String fieldName) {

        final T attribute = source != null ? getter.apply(source) : null;
        if (CollectionUtils.isNotEmpty(attribute)) {
            mergeIntoArray(result, fieldName, attribute);
        }
    }

    private <S, T extends String> void mergeStringAttributeIntoArray(
            ObjectNode result, S source, Function<S, T> getter, String fieldName) {

        final T attribute = source != null ? getter.apply(source) : null;
        if (StringUtils.isNotBlank(attribute)) {
            mergeIntoArray(result, fieldName, attribute);
        }
    }

    private void mergeIntoArray(ObjectNode result, String arrayField, String... values) {
        mergeIntoArray(result, arrayField, Arrays.asList(values));
    }

    private void mergeIntoArray(ObjectNode result, String arrayField, Collection<? extends String> values) {
        final JsonNode existingArray = result.get(arrayField);
        final Set<String> existingArrayValues = existingArray != null && isTextualArray(existingArray)
                ? stringArrayToStringSet(existingArray)
                : new LinkedHashSet<>();

        existingArrayValues.addAll(values);

        result.set(arrayField, stringsToStringArray(existingArrayValues.toArray(new String[0])));
    }

    private static String getTextValueFromNodeByPath(JsonNode node, String path) {
        final JsonNode nodeByPath = node != null ? node.get(path) : null;
        return nodeByPath != null && nodeByPath.isTextual() ? nodeByPath.textValue() : null;
    }

    private static String getTextValueFromNode(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private void populateFirstPartyDataAttributes(ObjectNode sourceNode, ObjectNode targetNode) {
        if (sourceNode == null || sourceNode.isNull()) {
            return;
        }

        final Iterator<String> fieldNames = sourceNode.fieldNames();
        while (fieldNames.hasNext()) {
            final String currentFieldName = fieldNames.next();
            final JsonNode currentField = sourceNode.get(currentFieldName);

            if (isTextualArray(currentField)) {
                mergeIntoArray(targetNode, currentFieldName, stringArrayToStringSet(currentField));
            } else if (currentField.isTextual()) {
                mergeIntoArray(targetNode, currentFieldName, currentField.textValue());
            } else if (currentField.isIntegralNumber()) {
                mergeIntoArray(targetNode, currentFieldName, Long.toString(currentField.longValue()));
            } else if (currentField.isBoolean()) {
                mergeIntoArray(targetNode, currentFieldName, Boolean.toString(currentField.booleanValue()));
            } else if (isBooleanArray(currentField)) {
                mergeIntoArray(targetNode, currentFieldName, booleanArrayToStringList(currentField));
            }
        }
    }

    private static boolean isTextualArray(JsonNode node) {
        return node.isArray() && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isTextual);
    }

    private static boolean isBooleanArray(JsonNode node) {
        return node.isArray() && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isBoolean);
    }

    private ArrayNode stringsToStringArray(String... values) {
        return stringsToStringArray(Arrays.asList(values));
    }

    private ArrayNode stringsToStringArray(Collection<String> values) {
        final ArrayNode arrayNode = mapper.mapper().createArrayNode();
        values.forEach(arrayNode::add);
        return arrayNode;
    }

    private static LinkedHashSet<String> stringArrayToStringSet(JsonNode stringArray) {
        return StreamSupport.stream(stringArray.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> booleanArrayToStringList(JsonNode booleanArray) {
        return StreamSupport.stream(booleanArray.spliterator(), false)
                .map(JsonNode::booleanValue)
                .map(value -> Boolean.toString(value))
                .collect(Collectors.toList());
    }

    private List<String> mapVendorsNamesToUrls(List<Metric> metrics) {
        if (metrics == null) {
            return null;
        }
        final List<String> vendorsUrls = metrics.stream()
                .filter(this::isMetricSupported)
                .map(metric -> ViewabilityVendors.valueOf(metric.getVendor()).getUrl())
                .collect(Collectors.toList());
        return vendorsUrls.isEmpty() ? null : vendorsUrls;
    }

    private Integer getMaxBids(ExtRequest extRequest) {
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final List<ExtRequestPrebidMultiBid> multibids = extRequestPrebid != null
                ? extRequestPrebid.getMultibid() : null;
        final ExtRequestPrebidMultiBid extRequestPrebidMultiBid =
                CollectionUtils.isNotEmpty(multibids) ? multibids.get(0) : null;
        final Integer multibidMaxBids = extRequestPrebidMultiBid != null ? extRequestPrebidMultiBid.getMaxBids() : null;

        return multibidMaxBids != null ? multibidMaxBids : 1;
    }

    private String getGpid(ObjectNode impExt) {
        final JsonNode gpidNode = impExt.get(FPD_GPID_FIELD);
        return gpidNode != null && gpidNode.isTextual() ? gpidNode.asText() : null;
    }

    private String getAdSlot(Imp imp, ExtImpContext context) {
        final ObjectNode contextDataNode = context != null ? context.getData() : null;
        final ObjectNode dataNode = toObjectNode(imp.getExt().get(FPD_DATA_FIELD));

        return getAdSlot(contextDataNode, dataNode);
    }

    private String getAdSlot(ObjectNode contextDataNode, ObjectNode dataNode) {
        return ObjectUtils.firstNonNull(
                // or imp[].ext.context.data.adserver.adslot
                getAdSlotFromAdServer(contextDataNode),
                // or imp[].ext.data.adserver.adslot
                getAdSlotFromAdServer(dataNode));
    }

    private String getAdSlotFromAdServer(JsonNode dataNode) {
        final ExtImpContextDataAdserver adServer = extImpContextDataAdserver(dataNode);
        return adServer != null && Objects.equals(adServer.getName(), FPD_ADSERVER_NAME_GAM)
                ? adServer.getAdslot()
                : null;
    }

    private ExtImpContextDataAdserver extImpContextDataAdserver(JsonNode contextData) {
        final JsonNode adServerNode = contextData != null ? contextData.get(FPD_ADSERVER_FIELD) : null;
        if (adServerNode == null || adServerNode.isNull()) {
            return null;
        }
        try {
            return mapper.mapper().convertValue(adServerNode, ExtImpContextDataAdserver.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static boolean isVideo(Imp imp) {
        final Video video = imp.getVideo();
        if (video != null) {
            // Do any other media types exist? Or check required video fields.
            return imp.getBanner() == null || isFullyPopulatedVideo(video);
        }
        return false;
    }

    private static boolean isFullyPopulatedVideo(Video video) {
        // These are just recommended video fields for XAPI
        return video.getMimes() != null && video.getProtocols() != null && video.getMaxduration() != null
                && video.getLinearity() != null && video.getApi() != null;
    }

    private static String referer(Site site) {
        return site != null ? site.getPage() : null;
    }

    private Video makeVideo(Imp imp, RubiconVideoParams rubiconVideoParams, ExtImpPrebid prebidImpExt, String referer) {
        final Video video = imp.getVideo();

        final Integer skip = rubiconVideoParams != null ? rubiconVideoParams.getSkip() : null;
        final Integer skipDelay = rubiconVideoParams != null ? rubiconVideoParams.getSkipdelay() : null;
        final Integer sizeId = rubiconVideoParams != null ? rubiconVideoParams.getSizeId() : null;

        final Integer resolvedSizeId = sizeId == null || sizeId == 0
                ? resolveVideoSizeId(video.getPlacement(), imp.getInstl())
                : sizeId;
        validateVideoSizeId(resolvedSizeId, referer, imp.getId());

        final String videoType = prebidImpExt != null && prebidImpExt.getIsRewardedInventory() != null
                && prebidImpExt.getIsRewardedInventory() == 1 ? "rewarded" : null;

        // optimization for empty ext params
        if (skip == null && skipDelay == null && resolvedSizeId == null && videoType == null) {
            return video;
        }

        return video.toBuilder()
                .ext(mapper.mapper().valueToTree(
                        RubiconVideoExt.of(skip, skipDelay, RubiconVideoExtRp.of(resolvedSizeId), videoType)))
                .build();
    }

    private static void validateVideoSizeId(Integer resolvedSizeId, String referer, String impId) {
        // log only 1% of cases to monitor how often video impressions does not have size id
        if (resolvedSizeId == null) {
            MISSING_VIDEO_SIZE_LOGGER.warn(String.format("RP adapter: video request with no size_id. Referrer URL = %s,"
                    + " impId = %s", referer, impId), 0.01d);
        }
    }

    private static Integer resolveVideoSizeId(Integer placement, Integer instl) {
        if (placement != null) {
            if (placement == 1) {
                return 201;
            }
            if (placement == 3) {
                return 203;
            }
        }

        if (instl != null && instl == 1) {
            return 202;
        }

        return null;
    }

    private static List<Format> overriddenSizes(ExtImpRubicon rubiconImpExt) {
        final List<Format> overriddenSizes;

        final List<Integer> sizeIds = rubiconImpExt.getSizes();
        if (sizeIds != null) {
            final List<Format> resolvedSizes = RubiconSize.idToSize(sizeIds);
            if (resolvedSizes.isEmpty()) {
                throw new PreBidException("Bad request.imp[].ext.rubicon.sizes");
            }
            overriddenSizes = resolvedSizes;
        } else {
            overriddenSizes = null;
        }

        return overriddenSizes;
    }

    private Banner makeBanner(Imp imp, List<Format> overriddenSizes) {
        final Banner banner = imp.getBanner();
        final boolean isInterstitial = Objects.equals(imp.getInstl(), 1);
        final List<Format> sizes = ObjectUtils.defaultIfNull(overriddenSizes, banner.getFormat());
        if (CollectionUtils.isEmpty(sizes)) {
            throw new PreBidException("rubicon imps must have at least one imp.format element");
        }

        return banner.toBuilder()
                .format(sizes)
                .ext(mapper.mapper().valueToTree(makeBannerExt(sizes, isInterstitial)))
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes, boolean isInterstitial) {
        final List<Integer> rubiconSizeIds = mapToRubiconSizeIds(sizes, isInterstitial);
        final Integer primarySizeId = rubiconSizeIds.get(0);
        final List<Integer> altSizeIds = rubiconSizeIds.size() > 1
                ? rubiconSizeIds.subList(1, rubiconSizeIds.size())
                : null;

        return RubiconBannerExt.of(RubiconBannerExtRp.of(primarySizeId, altSizeIds, "text/html"));
    }

    private static List<Integer> mapToRubiconSizeIds(List<Format> sizes, boolean isInterstitial) {
        final List<Integer> validRubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .sorted(RubiconSize.comparator())
                .collect(Collectors.toList());

        if (validRubiconSizeIds.isEmpty()) {
            // FIXME: Added 11.11.2020. short term solution for full screen interstitial adunits (PR #1003)
            if (isInterstitial) {
                validRubiconSizeIds.add(resolveNotStandardSizeForInstl(sizes.get(0)));
            } else {
                throw new PreBidException("No valid sizes");
            }
        }
        return validRubiconSizeIds;
    }

    private static int resolveNotStandardSizeForInstl(Format size) {
        return size.getH() > size.getW() ? PORTRAIT_MOBILE_SIZE_ID : LANDSCAPE_MOBILE_SIZE_ID;
    }

    private User makeUser(User user, ExtImpRubicon rubiconImpExt) {
        final String userId = user != null ? user.getId() : null;
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String resolvedId = userId == null ? resolveUserId(extUser) : null;
        final List<ExtUserEid> extUserEids = extUser != null ? extUser.getEids() : null;
        final String userBuyeruid = user != null ? user.getBuyeruid() : null;
        final String resolvedBuyeruid = userBuyeruid != null ? userBuyeruid : resolveBuyeruidFromEids(extUserEids);
        final Map<String, List<ExtUserEid>> sourceToUserEidExt = extUser != null
                ? specialExtUserEids(extUserEids)
                : null;
        final List<ExtUserTpIdRubicon> userExtTpIds = sourceToUserEidExt != null
                ? extractExtUserTpIds(sourceToUserEidExt)
                : null;
        final boolean hasStypeToRemove = hasStypeToRemove(extUserEids);
        final List<ExtUserEid> resolvedExtUserEids = hasStypeToRemove
                ? prepareExtUserEids(extUserEids)
                : extUserEids;
        final RubiconUserExtRp userExtRp = rubiconUserExtRp(user, rubiconImpExt, sourceToUserEidExt);
        final ObjectNode userExtData = extUser != null ? extUser.getData() : null;
        final String liverampId = extractLiverampId(sourceToUserEidExt);

        if (userExtRp == null
                && userExtTpIds == null
                && userExtData == null
                && liverampId == null
                && resolvedId == null
                && Objects.equals(userBuyeruid, resolvedBuyeruid)
                && !hasStypeToRemove) {
            return user;
        }

        final ExtUser userExt = extUser != null
                ? ExtUser.builder()
                .consent(extUser.getConsent())
                .eids(extUser.getEids())
                .eids(resolvedExtUserEids)
                .build()
                : ExtUser.builder().build();

        final RubiconUserExt rubiconUserExt = RubiconUserExt.builder()
                .rp(userExtRp)
                .tpid(userExtTpIds)
                .liverampIdl(liverampId)
                .build();

        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();

        return userBuilder
                .id(ObjectUtils.defaultIfNull(resolvedId, userId))
                .buyeruid(resolvedBuyeruid)
                .gender(null)
                .yob(null)
                .geo(null)
                .ext(mapper.fillExtension(userExt, rubiconUserExt))
                .build();
    }

    private String resolveUserId(ExtUser extUser) {
        final List<ExtUserEid> extUserEids = extUser != null ? extUser.getEids() : null;
        return CollectionUtils.emptyIfNull(extUserEids)
                .stream()
                .map(extUserEid -> getIdFromFirstUuidWithStypePpuid(extUserEid.getUids()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String getIdFromFirstUuidWithStypePpuid(List<ExtUserEidUid> extUserEidUids) {
        return CollectionUtils.emptyIfNull(extUserEidUids).stream()
                .filter(Objects::nonNull)
                .filter(extUserEidUid -> Objects.equals(PPUID_STYPE, getUserEidUidStype(extUserEidUid)))
                .map(ExtUserEidUid::getId)
                .findFirst()
                .orElse(null);
    }

    private String getUserEidUidStype(ExtUserEidUid extUserEidUid) {
        final ExtUserEidUidExt extUserEidUidExt = extUserEidUid.getExt();
        return extUserEidUidExt != null ? extUserEidUidExt.getStype() : null;
    }

    private boolean hasStypeToRemove(List<ExtUserEid> extUserEids) {
        return CollectionUtils.emptyIfNull(extUserEids).stream()
                .filter(Objects::nonNull)
                .map(ExtUserEid::getUids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(ExtUserEidUid::getExt)
                .filter(Objects::nonNull)
                .map(ExtUserEidUidExt::getStype)
                .anyMatch(STYPE_TO_REMOVE::contains);
    }

    private List<ExtUserEid> prepareExtUserEids(List<ExtUserEid> extUserEids) {
        return CollectionUtils.emptyIfNull(extUserEids).stream()
                .filter(Objects::nonNull)
                .map(RubiconBidder::prepareExtUserEid)
                .collect(Collectors.toList());
    }

    private static ExtUserEid prepareExtUserEid(ExtUserEid extUserEid) {
        final List<ExtUserEidUid> extUserEidUids = CollectionUtils.emptyIfNull(extUserEid.getUids()).stream()
                .filter(Objects::nonNull)
                .map(RubiconBidder::cleanExtUserEidUidStype)
                .collect(Collectors.toList());
        return ExtUserEid.of(extUserEid.getSource(), extUserEid.getId(), extUserEidUids, extUserEid.getExt());
    }

    private static ExtUserEidUid cleanExtUserEidUidStype(ExtUserEidUid extUserEidUid) {
        final ExtUserEidUidExt extUserEidUidExt = extUserEidUid.getExt();
        return extUserEidUidExt == null || !STYPE_TO_REMOVE.contains(extUserEidUidExt.getStype())
                ? extUserEidUid
                : ExtUserEidUid.of(extUserEidUid.getId(), extUserEidUid.getAtype(),
                ExtUserEidUidExt.of(extUserEidUidExt.getRtiPartner(), null));
    }

    private static String resolveBuyeruidFromEids(List<ExtUserEid> eids) {
        return CollectionUtils.emptyIfNull(eids).stream()
                .filter(Objects::nonNull)
                .filter(eid -> SOURCE_RUBICON.equals(eid.getSource()))
                .map(ExtUserEid::getUids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(RubiconBidder::validateExtUserEidUidForUserBuyeruid)
                .map(ExtUserEidUid::getId)
                .findFirst()
                .orElse(null);

    }

    private static boolean validateExtUserEidUidForUserBuyeruid(ExtUserEidUid uid) {
        final ExtUserEidUidExt uidExt = ObjectUtil.getIfNotNull(uid, ExtUserEidUid::getExt);
        final String uidExtStype = ObjectUtil.getIfNotNull(uidExt, ExtUserEidUidExt::getStype);

        return StringUtils.equalsAny(uidExtStype, PPUID_STYPE, OTHER_STYPE);
    }

    private static Map<String, List<ExtUserEid>> specialExtUserEids(List<ExtUserEid> eids) {
        if (CollectionUtils.isEmpty(eids)) {
            return null;
        }
        return eids.stream()
                .filter(extUserEid -> StringUtils.equalsAny(extUserEid.getSource(),
                        ADSERVER_EID, LIVEINTENT_EID, LIVERAMP_EID))
                .filter(extUserEid -> CollectionUtils.isNotEmpty(extUserEid.getUids()))
                .collect(Collectors.groupingBy(ExtUserEid::getSource));
    }

    /**
     * Analyzes request.user.ext.eids and returns a list of new {@link ExtUserTpIdRubicon}s for supported vendors.
     */
    private static List<ExtUserTpIdRubicon> extractExtUserTpIds(Map<String, List<ExtUserEid>> specialExtUserEids) {
        final List<ExtUserTpIdRubicon> result = new ArrayList<>();

        specialExtUserEids.getOrDefault(ADSERVER_EID, Collections.emptyList()).stream()
                .map(extUserEid -> extUserTpIdForAdServer(extUserEid.getUids().get(0)))
                .filter(Objects::nonNull)
                .forEach(result::add);

        specialExtUserEids.getOrDefault(LIVEINTENT_EID, Collections.emptyList()).stream()
                .map(extUserEid -> extUserTpIdForLiveintent(extUserEid.getUids().get(0)))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(result::add);

        return result.isEmpty() ? null : result;
    }

    /**
     * Extracts {@link ExtUserTpIdRubicon} for AdServer.
     */
    private static ExtUserTpIdRubicon extUserTpIdForAdServer(ExtUserEidUid adServerEidUid) {
        final ExtUserEidUidExt ext = adServerEidUid != null ? adServerEidUid.getExt() : null;
        return ext != null && Objects.equals(ext.getRtiPartner(), "TDID")
                ? ExtUserTpIdRubicon.of("tdid", adServerEidUid.getId())
                : null;
    }

    /**
     * Extracts {@link ExtUserTpIdRubicon} for Liveintent.
     */
    private static ExtUserTpIdRubicon extUserTpIdForLiveintent(ExtUserEidUid adServerEidUid) {
        final String id = adServerEidUid != null ? adServerEidUid.getId() : null;
        return id != null ? ExtUserTpIdRubicon.of(LIVEINTENT_EID, id) : null;
    }

    private RubiconUserExtRp rubiconUserExtRp(User user,
                                              ExtImpRubicon rubiconImpExt,
                                              Map<String, List<ExtUserEid>> sourceToUserEidExt) {

        final JsonNode target = rubiconUserExtRpTarget(sourceToUserEidExt, rubiconImpExt.getVisitor(), user);

        return target != null ? RubiconUserExtRp.of(target) : null;
    }

    private JsonNode rubiconUserExtRpTarget(Map<String, List<ExtUserEid>> sourceToUserEidExt,
                                            ObjectNode visitor,
                                            User user) {

        final ObjectNode result = existingRubiconUserExtRpTarget(user);

        populateFirstPartyDataAttributes(visitor, result);

        copyLiveintentSegment(sourceToUserEidExt, result);

        if (user != null) {
            mergeFirstPartyDataFromUser(user.getExt(), result);

            enrichWithIabAttribute(result, user.getData(), USER_SEGTAXES);
        }

        return result.size() > 0 ? result : null;
    }

    private ObjectNode existingRubiconUserExtRpTarget(User user) {
        final ExtUser userExt = user != null ? user.getExt() : null;
        final RubiconUserExt userRubiconExt = userExt != null
                ? mapper.mapper().convertValue(userExt, RubiconUserExt.class)
                : null;
        final RubiconUserExtRp userRubiconRpExt = userRubiconExt != null ? userRubiconExt.getRp() : null;
        final JsonNode target = userRubiconRpExt != null ? userRubiconRpExt.getTarget() : null;

        return target != null && target.isObject() ? (ObjectNode) target : mapper.mapper().createObjectNode();
    }

    private static void copyLiveintentSegment(Map<String, List<ExtUserEid>> sourceToUserEidExt, ObjectNode result) {
        if (sourceToUserEidExt != null && CollectionUtils.isNotEmpty(sourceToUserEidExt.get(LIVEINTENT_EID))) {
            final ObjectNode ext = sourceToUserEidExt.get(LIVEINTENT_EID).get(0).getExt();
            final JsonNode segment = ext != null ? ext.get("segments") : null;

            if (segment != null) {
                result.set("LIseg", segment);
            }
        }
    }

    private void mergeFirstPartyDataFromUser(ExtUser userExt, ObjectNode result) {
        // merge OPENRTB.user.ext.data.* to XAPI.user.ext.rp.target.*
        if (userExt != null) {
            populateFirstPartyDataAttributes(userExt.getData(), result);
        }
    }

    private static void enrichWithIabAttribute(ObjectNode target, List<Data> data, Set<Integer> segtaxValues) {
        final List<String> iabValue = CollectionUtils.emptyIfNull(data).stream()
                .filter(Objects::nonNull)
                .filter(dataRecord -> containsSegtaxValue(dataRecord.getExt(), segtaxValues))
                .map(Data::getSegment)
                .filter(Objects::nonNull)
                .flatMap(segments -> segments.stream()
                        .map(Segment::getId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(iabValue)) {
            final ArrayNode iab = target.putArray("iab");
            iabValue.forEach(iab::add);
        }
    }

    private static boolean containsSegtaxValue(ObjectNode ext, Set<Integer> segtaxValues) {
        final JsonNode taxonomyName = ext != null ? ext.get("segtax") : null;

        return taxonomyName != null && taxonomyName.isInt() && segtaxValues.contains(taxonomyName.intValue());
    }

    private static String extractLiverampId(Map<String, List<ExtUserEid>> sourceToUserEidExt) {
        final List<ExtUserEid> liverampEids = MapUtils.emptyIfNull(sourceToUserEidExt).get(LIVERAMP_EID);
        for (ExtUserEid extUserEid : CollectionUtils.emptyIfNull(liverampEids)) {
            return extUserEid.getUids().stream()
                    .map(ExtUserEidUid::getId)
                    .filter(StringUtils::isNotEmpty)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private Device makeDevice(Device device) {
        return device == null ? null : device.toBuilder()
                .ext(mapper.fillExtension(
                        ExtDevice.empty(),
                        RubiconDeviceExt.of(RubiconDeviceExtRp.of(device.getPxratio()))))
                .build();
    }

    private Site makeSite(Site site, String impLanguage, ExtImpRubicon rubiconImpExt) {
        if (site == null && StringUtils.isBlank(impLanguage)) {
            return null;
        }

        return site == null
                ? Site.builder()
                .content(makeSiteContent(null, impLanguage))
                .build()
                : site.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .content(makeSiteContent(site.getContent(), impLanguage))
                .ext(makeSiteExt(site, rubiconImpExt))
                .build();
    }

    private static Content makeSiteContent(Content siteContent, String impLanguage) {
        if (StringUtils.isBlank(impLanguage)) {
            return siteContent;
        }
        if (siteContent == null) {
            return Content.builder().language(impLanguage).build();
        } else {
            return StringUtils.isBlank(siteContent.getLanguage())
                    ? siteContent.toBuilder().language(impLanguage).build()
                    : siteContent;
        }
    }

    private Publisher makePublisher(ExtImpRubicon rubiconImpExt) {
        return Publisher.builder()
                .ext(makePublisherExt(rubiconImpExt))
                .build();
    }

    private ExtPublisher makePublisherExt(ExtImpRubicon rubiconImpExt) {
        return mapper.fillExtension(
                ExtPublisher.empty(),
                RubiconPubExt.of(RubiconPubExtRp.of(rubiconImpExt.getAccountId())));
    }

    private ExtSite makeSiteExt(Site site, ExtImpRubicon rubiconImpExt) {
        final ExtSite extSite = site != null ? site.getExt() : null;
        final Integer siteExtAmp = extSite != null ? extSite.getAmp() : null;
        final Content siteContent = site != null ? site.getContent() : null;
        final List<Data> siteContentData = siteContent != null ? siteContent.getData() : null;
        ObjectNode target = null;

        if (CollectionUtils.isNotEmpty(siteContentData)) {
            target = existingRubiconSiteExtRpTargetOrEmptyNode(extSite);
            enrichWithIabAttribute(target, siteContentData, SITE_SEGTAXES);
        }

        return mapper.fillExtension(
                ExtSite.of(siteExtAmp, null),
                RubiconSiteExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId(),
                        target != null && !target.isEmpty() ? target : null)));
    }

    private ObjectNode existingRubiconSiteExtRpTargetOrEmptyNode(ExtSite siteExt) {
        final RubiconSiteExt rubiconSiteExt = siteExt != null
                ? mapper.mapper().convertValue(siteExt, RubiconSiteExt.class)
                : null;
        final RubiconSiteExtRp rubiconSiteExtRp = rubiconSiteExt != null ? rubiconSiteExt.getRp() : null;
        final JsonNode target = rubiconSiteExtRp != null ? rubiconSiteExtRp.getTarget() : null;

        return target != null && target.isObject() ? (ObjectNode) target : mapper.mapper().createObjectNode();
    }

    private App makeApp(App app, ExtImpRubicon rubiconImpExt) {
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(makeAppExt(rubiconImpExt))
                .build();
    }

    private ExtApp makeAppExt(ExtImpRubicon rubiconImpExt) {
        return mapper.fillExtension(ExtApp.of(null, null),
                RubiconAppExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId(), null)));
    }

    private static Source makeSource(Source source, String pchain) {
        if (StringUtils.isNotEmpty(pchain)) {
            final Source.SourceBuilder builder = source != null ? source.toBuilder() : Source.builder();
            return builder.pchain(pchain).build();
        }
        return source;
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, String uri) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .body(mapper.encodeToBytes(bidRequest))
                .headers(headers)
                .payload(bidRequest)
                .build();
    }

    private static boolean hasDeals(Imp imp) {
        return imp.getPmp() != null && CollectionUtils.isNotEmpty(imp.getPmp().getDeals());
    }

    private List<HttpRequest<BidRequest>> createDealsRequests(BidRequest bidRequest, String uri) {
        final Imp singleImp = bidRequest.getImp().get(0);
        return singleImp.getPmp().getDeals().stream()
                .map(deal -> mapper.mapper().convertValue(deal.getExt(), ExtDeal.class))
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .filter(Objects::nonNull)
                .map(lineItem -> createLineItemBidRequest(lineItem, bidRequest, singleImp))
                .map((BidRequest request) -> createHttpRequest(request, uri))
                .collect(Collectors.toList());
    }

    private BidRequest createLineItemBidRequest(ExtDealLine lineItem, BidRequest bidRequest, Imp imp) {
        final Imp dealsImp = imp.toBuilder()
                .banner(modifyBanner(imp.getBanner(), lineItem.getSizes()))
                .ext(modifyRubiconImpExt(imp.getExt(), bidRequest.getExt(), lineItem.getExtLineItemId(),
                        getAdSlot(imp, extImpContext(imp))))
                .build();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(dealsImp))
                .build();
    }

    private static Banner modifyBanner(Banner banner, List<Format> sizes) {
        return CollectionUtils.isEmpty(sizes) || banner == null ? banner : banner.toBuilder().format(sizes).build();
    }

    private ObjectNode modifyRubiconImpExt(ObjectNode impExtNode, ExtRequest extRequest, String extLineItemId,
                                           String adSlot) {
        final RubiconImpExt rubiconImpExt = mapper.mapper().convertValue(impExtNode, RubiconImpExt.class);
        final RubiconImpExtRp impExtRp = rubiconImpExt.getRp();

        final ObjectNode targetNode = impExtRp.getTarget() == null || impExtRp.getTarget().isNull()
                ? mapper.mapper().createObjectNode() : (ObjectNode) impExtRp.getTarget();

        final ObjectNode modifiedTargetNode = targetNode.put("line_item", extLineItemId);
        final RubiconImpExtRp modifiedImpExtRp = RubiconImpExtRp.of(impExtRp.getZoneId(), modifiedTargetNode,
                impExtRp.getTrack());

        return mapper.mapper().valueToTree(RubiconImpExt.of(modifiedImpExtRp, rubiconImpExt.getViewabilityvendors(),
                getMaxBids(extRequest), adSlot));
    }

    private List<BidderBid> extractBids(BidRequest prebidRequest,
                                        BidRequest bidRequest,
                                        RubiconBidResponse bidResponse,
                                        List<BidderError> errors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(prebidRequest, bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest prebidRequest,
                                             BidRequest bidRequest,
                                             RubiconBidResponse bidResponse,
                                             List<BidderError> errors) {
        final Map<String, Imp> idToImp = prebidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        final Float cpmOverrideFromRequest = cpmOverrideFromRequest(prebidRequest);
        final BidType bidType = bidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> updateSeatBids(seatBid, errors))
                .map(RubiconSeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> updateBid(bid, idToImp.get(bid.getImpid()), cpmOverrideFromRequest, bidResponse))
                .map(bid -> BidderBid.of(bid, bidType, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private RubiconSeatBid updateSeatBids(RubiconSeatBid seatBid, List<BidderError> errors) {
        final String buyer = seatBid.getBuyer();
        final int networkId = NumberUtils.toInt(buyer, 0);
        if (networkId <= 0) {
            return seatBid;
        }
        final List<Bid> updatedBids = seatBid.getBid().stream()
                .map(bid -> insertNetworkIdToMeta(bid, networkId, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return seatBid.toBuilder().bid(updatedBids).build();
    }

    private Bid insertNetworkIdToMeta(Bid bid, int networkId, List<BidderError> errors) {
        final ObjectNode bidExt = bid.getExt();
        final ExtPrebid<ExtBidPrebid, ObjectNode> extPrebid;
        try {
            extPrebid = getExtPrebid(bidExt, bid.getId());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        final ExtBidPrebid extBidPrebid = extPrebid != null ? extPrebid.getPrebid() : null;
        final ObjectNode meta = extBidPrebid != null ? extBidPrebid.getMeta() : null;

        final ObjectNode updatedMeta = meta != null ? meta : mapper.mapper().createObjectNode();
        updatedMeta.set("networkId", IntNode.valueOf(networkId));

        final ExtBidPrebid modifiedExtBidPrebid = extBidPrebid != null
                ? extBidPrebid.toBuilder().meta(updatedMeta).build()
                : ExtBidPrebid.builder().meta(updatedMeta).build();

        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();
        updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(modifiedExtBidPrebid));

        return bid.toBuilder().ext(updatedBidExt).build();
    }

    private ExtPrebid<ExtBidPrebid, ObjectNode> getExtPrebid(ObjectNode bidExt, String bidId) {
        try {
            return bidExt != null ? mapper.mapper().convertValue(bidExt, EXT_PREBID_TYPE_REFERENCE) : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Invalid ext passed in bid with id: %s", bidId));
        }
    }

    private Bid updateBid(Bid bid, Imp imp, Float cpmOverrideFromRequest, RubiconBidResponse bidResponse) {
        String bidId = bid.getId();
        if (generateBidId) {
            // Since Rubicon XAPI returns openrtb_response.seatbid.bid.id not unique enough
            // generate new value for it
            bidId = UUID.randomUUID().toString();
        } else if (Objects.equals(bid.getId(), "0")) {
            // Since Rubicon XAPI returns only one bid per response
            // copy bidResponse.bidid to openrtb_response.seatbid.bid.id
            bidId = bidResponse.getBidid();
        }

        // Unconditionally set price if coming from CPM override
        final Float cpmOverride = ObjectUtils.defaultIfNull(cpmOverrideFromImp(imp), cpmOverrideFromRequest);
        final BigDecimal bidPrice = cpmOverride != null
                ? new BigDecimal(String.valueOf(cpmOverride))
                : bid.getPrice();

        return bid.toBuilder()
                .id(bidId)
                .price(bidPrice)
                .build();
    }

    private Float cpmOverrideFromRequest(BidRequest bidRequest) {
        final RubiconExtPrebidBiddersBidder bidder = extPrebidBiddersRubicon(bidRequest.getExt());
        final RubiconExtPrebidBiddersBidderDebug debug = bidder != null ? bidder.getDebug() : null;
        return debug != null ? debug.getCpmoverride() : null;
    }

    private Float cpmOverrideFromImp(Imp imp) {
        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> extPrebid = imp != null ? parseRubiconExt(imp) : null;
        final ExtImpRubicon bidder = extPrebid != null ? extPrebid.getBidder() : null;
        final ExtImpRubiconDebug debug = bidder != null ? bidder.getDebug() : null;
        return debug != null ? debug.getCpmoverride() : null;
    }

    private static BidType bidType(BidRequest bidRequest) {
        return isVideo(bidRequest.getImp().get(0)) ? BidType.video : BidType.banner;
    }

    private ObjectNode toObjectNode(JsonNode node) {
        return node != null && node.isObject()
                ? (ObjectNode) node
                : mapper.mapper().createObjectNode();
    }
}
