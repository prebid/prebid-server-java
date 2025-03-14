package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.PriceFloorInfo;
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
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRpRtb;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.request.RubiconNative;
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
import org.prebid.server.bidder.rubicon.proto.response.RubiconBid;
import org.prebid.server.bidder.rubicon.proto.response.RubiconBidResponse;
import org.prebid.server.bidder.rubicon.proto.response.RubiconSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.PriceFloorResolver;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContextDataAdserver;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubiconDebug;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.version.PrebidVersionProvider;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RubiconBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RubiconBidder.class);
    private static final ConditionalLogger MISSING_VIDEO_SIZE_LOGGER =
            new ConditionalLogger("missing_video_size", logger);

    private static final String TK_XINT_QUERY_PARAMETER = "tk_xint";
    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private static final String FPD_GPID_FIELD = "gpid";
    private static final String FPD_SKADN_FIELD = "skadn";
    private static final String FPD_PAGE_FIELD = "page";
    private static final String FPD_DATA_FIELD = "data";
    private static final String FPD_DATA_PBADSLOT_FIELD = "pbadslot";
    private static final String FPD_ADSERVER_FIELD = "adserver";
    private static final String FPD_ADSERVER_NAME_GAM = "gam";
    private static final String FPD_KEYWORDS_FIELD = "keywords";
    private static final String DFP_ADUNIT_CODE_FIELD = "dfp_ad_unit_code";
    private static final String STYPE_FIELD = "stype";
    private static final String TID_FIELD = "tid";
    private static final String PREBID_EXT = "prebid";
    private static final String PBS_LOGIN = "pbs_login";
    private static final String PBS_VERSION = "pbs_version";
    private static final String PBS_URL = "pbs_url";

    private static final String PPUID_STYPE = "ppuid";
    private static final String SHA256EMAIL_STYPE = "sha256email";
    private static final String DMP_STYPE = "dmp";
    private static final String XAPI_CURRENCY = "USD";

    private static final int MAX_NUMBER_OF_SEGMENTS = 100;
    private static final String SEGTAX_IAB = "iab";
    private static final String SEGTAX_TAX = "tax";
    private static final String SEGTAX = "segtax";

    private static final Set<Integer> USER_SEGTAXES = Set.of(4);
    private static final Set<Integer> SITE_SEGTAXES = Set.of(1, 2, 5, 6, 7);

    private static final Set<String> STYPE_TO_REMOVE = new HashSet<>(Arrays.asList(PPUID_STYPE, SHA256EMAIL_STYPE,
            DMP_STYPE));
    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final boolean DEFAULT_MULTIFORMAT_VALUE = false;

    private final String bidderName;
    private final String endpointUrl;
    private final String externalUrl;
    private final String xapiUsername;
    private final Set<String> supportedVendors;
    private final boolean generateBidId;
    private final String apexRendererUrl;
    private final CurrencyConversionService currencyConversionService;
    private final PriceFloorResolver floorResolver;
    private final PrebidVersionProvider versionProvider;
    private final IdGenerator idGenerator;
    private final JacksonMapper mapper;

    private final MultiMap headers;

    public RubiconBidder(String bidderName,
                         String endpoint,
                         String externalUrl,
                         String xapiUsername,
                         String xapiPassword,
                         List<String> supportedVendors,
                         boolean generateBidId,
                         String apexRendererUrl,
                         CurrencyConversionService currencyConversionService,
                         PriceFloorResolver floorResolver,
                         PrebidVersionProvider versionProvider,
                         IdGenerator idGenerator,
                         JacksonMapper mapper) {

        this.bidderName = Objects.requireNonNull(bidderName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
        this.xapiUsername = Objects.requireNonNull(xapiUsername);
        this.supportedVendors = Set.copyOf(Objects.requireNonNull(supportedVendors));
        this.generateBidId = generateBidId;
        this.apexRendererUrl = apexRendererUrl;
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.floorResolver = Objects.requireNonNull(floorResolver);
        this.versionProvider = Objects.requireNonNull(versionProvider);
        this.idGenerator = Objects.requireNonNull(idGenerator);
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

        final Map<Imp, ExtImpRubicon> impToImpExt = parseRubiconImpExts(imps, errors);
        final String language = firstImpExtLanguage(impToImpExt.values());
        final String uri = makeUri(bidRequest);

        for (Map.Entry<Imp, ExtImpRubicon> impToExt : impToImpExt.entrySet()) {
            try {
                final Imp imp = impToExt.getKey();
                final ExtImpRubicon impExt = impToExt.getValue();
                final String pbBidId = generateBidId ? idGenerator.generateId() : null;
                final List<BidRequest> impBidRequests = isMultiformatEnabled(impExt)
                        ? createMultiFormatRequests(bidRequest, imp, impExt, pbBidId, language, errors)
                        : List.of(createSingleRequest(bidRequest, imp, impExt, pbBidId, null, language, errors));

                httpRequests.addAll(createImpHttpRequests(imp, impBidRequests, uri));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private List<BidRequest> createMultiFormatRequests(BidRequest bidRequest,
                                                       Imp imp,
                                                       ExtImpRubicon impExt,
                                                       String pbBidId,
                                                       String language,
                                                       List<BidderError> errors) {

        final Map<ImpMediaType, Imp> impByType = splitByMediaType(imp);
        final Set<ImpMediaType> formats = impByType.keySet();
        if (formats.size() == 1) {
            return Collections.singletonList(
                    createSingleRequest(bidRequest, imp, impExt, pbBidId, null, language, errors));
        }

        final List<BidRequest> bidRequests = new ArrayList<>();
        for (Imp singleFormatImp : impByType.values()) {
            try {
                bidRequests.add(
                        createSingleRequest(bidRequest, singleFormatImp, impExt, pbBidId, formats, language, errors));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return bidRequests;
    }

    private Map<ImpMediaType, Imp> splitByMediaType(Imp imp) {
        final Map<ImpMediaType, Imp> impByType = new HashMap<>();
        if (imp.getBanner() != null) {
            impByType.put(ImpMediaType.banner, imp.toBuilder().video(null).xNative(null).audio(null).build());
        }
        if (imp.getVideo() != null) {
            impByType.put(ImpMediaType.video, imp.toBuilder().banner(null).xNative(null).audio(null).build());
        }
        if (imp.getXNative() != null) {
            impByType.put(ImpMediaType.xNative, imp.toBuilder().banner(null).video(null).audio(null).build());
        }
        if (imp.getAudio() != null) {
            impByType.put(ImpMediaType.audio, imp.toBuilder().banner(null).video(null).xNative(null).build());
        }

        return impByType;
    }

    private List<HttpRequest<BidRequest>> createImpHttpRequests(Imp imp, List<BidRequest> impBidRequests, String uri) {
        if (hasDeals(imp)) {
            return impBidRequests.stream()
                    .map(request -> createDealsRequests(request, uri))
                    .flatMap(Collection::stream)
                    .toList();
        }
        return impBidRequests.stream().map(request -> createHttpRequest(request, uri)).toList();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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
                .collect(Collectors.toMap(RubiconTargeting::getKey, targeting -> targeting.getValues().getFirst()))
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
        return ObjectUtils.anyNotNull(imp.getVideo(), imp.getBanner(), imp.getXNative());
    }

    private BidderError impTypeErrorMessage(Imp imp) {
        final BidType type = resolveExpectedBidType(imp);
        return BidderError.badInput(
                "Impression with id %s rejected with invalid type `%s`. Allowed types are [banner, video, native]"
                        .formatted(imp.getId(), type != null ? type.name() : "unknown"));
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

    private Map<Imp, ExtImpRubicon> parseRubiconImpExts(List<Imp> imps, List<BidderError> errors) {
        final Map<Imp, ExtImpRubicon> impToImpExt = new HashMap<>();
        for (final Imp imp : imps) {
            try {
                impToImpExt.put(imp, parseRubiconExt(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return impToImpExt;
    }

    private ExtImpRubicon parseRubiconExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static boolean isMultiformatEnabled(ExtImpRubicon extImp) {
        return Optional.ofNullable(extImp)
                .map(ExtImpRubicon::getBidOnMultiFormat)
                .orElse(DEFAULT_MULTIFORMAT_VALUE);
    }

    private static String firstImpExtLanguage(Collection<ExtImpRubicon> rubiconImpExts) {
        return rubiconImpExts.stream()
                .filter(Objects::nonNull)
                .map(ExtImpRubicon::getVideo)
                .filter(Objects::nonNull)
                .map(RubiconVideoParams::getLanguage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private BidRequest createSingleRequest(BidRequest bidRequest,
                                           Imp imp,
                                           ExtImpRubicon extImpRubicon,
                                           String pbBidId,
                                           Set<ImpMediaType> formats,
                                           String impLanguage,
                                           List<BidderError> errors) {

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(makeImp(imp, extImpRubicon, bidRequest, pbBidId, formats, errors)))
                .user(downgradeUserConsent(makeUser(bidRequest.getUser(), extImpRubicon)))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(bidRequest.getSite(), impLanguage, extImpRubicon))
                .app(makeApp(bidRequest.getApp(), extImpRubicon))
                .source(makeSource(bidRequest.getSource()))
                .cur(null) // suppress currencies
                .regs(makeRegs(bidRequest.getRegs()))
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
                throw new PreBidException("Cant add the tk_xint value for url: " + tkXint, e);
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
                        ExtImpRubicon extImpRubicon,
                        BidRequest bidRequest,
                        String pbImpId,
                        Set<ImpMediaType> formats,
                        List<BidderError> errors) {

        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        final ExtRequest extRequest = bidRequest.getExt();
        final ImpMediaType impType = impType(imp);
        final List<String> priceFloorsWarnings = new ArrayList<>();

        final PriceFloorResult priceFloorResult = resolvePriceFloors(bidRequest, imp, impType, priceFloorsWarnings);
        final Set<ImpMediaType> resolvedFormats = ObjectUtils.defaultIfNull(extImpRubicon.getFormats(), formats);

        final BigDecimal ipfFloor = ObjectUtil.getIfNotNull(priceFloorResult, PriceFloorResult::getFloorValue);
        final String ipfCurrency = ipfFloor != null
                ? resolveCurrencyFromFloorResult(
                ObjectUtil.getIfNotNull(priceFloorResult, PriceFloorResult::getCurrency),
                bidRequest,
                imp,
                errors)
                : null;

        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(mapper.mapper().valueToTree(
                        makeImpExt(imp, extImpRubicon, resolvedFormats, site, app, extRequest, pbImpId)));

        final BigDecimal resolvedBidFloor = ipfFloor != null
                ? convertToXAPICurrency(ipfFloor, ipfCurrency, imp, bidRequest)
                : resolveBidFloorFromImp(imp, bidRequest, errors);

        if (resolvedBidFloor != null) {
            builder
                    .bidfloorcur(XAPI_CURRENCY)
                    .bidfloor(resolvedBidFloor);
        }

        switch (impType) {
            case video -> builder
                    .banner(null)
                    .xNative(null)
                    .rwdd(null)
                    .video(makeVideo(imp, extImpRubicon.getVideo(), referer(site)));
            case banner -> builder
                    .banner(makeBanner(imp))
                    .xNative(null)
                    .video(null);
            default -> builder
                    .video(null)
                    .xNative(makeNative(imp));
        }

        processWarnings(errors, priceFloorsWarnings);

        return builder.build();
    }

    private PriceFloorResult resolvePriceFloors(BidRequest bidRequest,
                                                Imp imp,
                                                ImpMediaType mediaType,
                                                List<String> warnings) {

        return floorResolver.resolve(
                bidRequest,
                extractFloorRules(bidRequest),
                imp,
                mediaType,
                null,
                bidderName,
                warnings);
    }

    private static PriceFloorRules extractFloorRules(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
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

    private BigDecimal resolveBidFloorFromImp(Imp imp, BidRequest bidRequest, List<BidderError> errors) {
        final BigDecimal resolvedBidFloorPrice = resolveBidFloorPrice(imp);
        if (resolvedBidFloorPrice == null) {
            return null;
        }

        return convertToXAPICurrency(
                resolvedBidFloorPrice,
                resolveBidFloorCurrency(imp, bidRequest, errors),
                imp,
                bidRequest);
    }

    private BigDecimal convertToXAPICurrency(BigDecimal value,
                                             String fromCurrency,
                                             Imp imp,
                                             BidRequest bidRequest) {

        return ObjectUtils.notEqual(fromCurrency, XAPI_CURRENCY)
                ? convertBidFloorCurrency(value, fromCurrency, imp, bidRequest)
                : value;
    }

    private static BigDecimal resolveBidFloorPrice(Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        return bidFloor != null && bidFloor.compareTo(BigDecimal.ZERO) >= 0 ? bidFloor : null;
    }

    private static String resolveBidFloorCurrency(Imp imp, BidRequest bidRequest, List<BidderError> errors) {
        final String bidFloorCurrency = imp.getBidfloorcur();
        if (StringUtils.isBlank(bidFloorCurrency)) {
            if (isDebugEnabled(bidRequest)) {
                errors.add(BidderError.badInput("Imp `%s` floor provided with no currency, assuming %s"
                        .formatted(imp.getId(), XAPI_CURRENCY)));
            }
            return XAPI_CURRENCY;
        }
        return bidFloorCurrency;
    }

    private static String resolveCurrencyFromFloorResult(String floorCurrency,
                                                         BidRequest bidRequest,
                                                         Imp imp,
                                                         List<BidderError> errors) {

        if (StringUtils.isBlank(floorCurrency)) {
            if (isDebugEnabled(bidRequest)) {
                errors.add(BidderError.badInput("Ipf for imp `%s` provided floor with no currency, assuming %s"
                        .formatted(imp.getId(), XAPI_CURRENCY)));
            }
            return XAPI_CURRENCY;
        }
        return floorCurrency;
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
            throw new PreBidException(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s` with a reason: %s"
                            .formatted(bidFloorCurrency, XAPI_CURRENCY, imp.getId(), e.getMessage()));
        }
    }

    private RubiconImpExt makeImpExt(Imp imp,
                                     ExtImpRubicon rubiconImpExt,
                                     Set<ImpMediaType> formats,
                                     Site site,
                                     App app,
                                     ExtRequest extRequest,
                                     String pbBidId) {

        final RubiconImpExtRpRtb rubiconImpExtRpRtb = CollectionUtils.isNotEmpty(formats)
                ? RubiconImpExtRpRtb.of(formats)
                : null;

        final RubiconImpExtRp rubiconImpExtRp = RubiconImpExtRp.of(
                rubiconImpExt.getZoneId(),
                makeTarget(imp, rubiconImpExt, site, app),
                RubiconImpExtRpTrack.of("", ""),
                rubiconImpExtRpRtb,
                pbBidId);

        return RubiconImpExt.builder()
                .rp(rubiconImpExtRp)
                .viewabilityvendors(mapVendorsNamesToUrls(imp.getMetric()))
                .maxbids(getMaxBids(extRequest))
                .gpid(getGpid(imp.getExt()))
                .skadn(getSkadn(imp.getExt()))
                .tid(getTid(imp.getExt()))
                .build();
    }

    private JsonNode makeTarget(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app) {
        final ObjectNode result = mapper.mapper().createObjectNode();

        populateFirstPartyDataAttributes(rubiconImpExt.getInventory(), result);

        mergeFirstPartyDataFromSite(site, result);
        mergeFirstPartyDataFromApp(app, result);
        mergeFirstPartyDataFromImp(imp, rubiconImpExt, result);

        result.put(PBS_LOGIN, xapiUsername);
        result.put(PBS_VERSION, versionProvider.getNameVersionRecord());
        result.put(PBS_URL, externalUrl);

        return result;
    }

    private void mergeFirstPartyDataFromSite(Site site, ObjectNode result) {
        // merge OPENRTB.site.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
        final ExtSite siteExt = site != null ? site.getExt() : null;
        if (siteExt != null) {
            populateFirstPartyDataAttributes(siteExt.getData(), result);
        }

        // merge OPENRTB.site.page to every impression XAPI.imp[].ext.rp.target.page
        mergeStringAttributeIntoArray(result, site, Site::getPage, FPD_PAGE_FIELD);
    }

    private void mergeFirstPartyDataFromApp(App app, ObjectNode result) {
        // merge OPENRTB.app.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
        final ExtApp appExt = app != null ? app.getExt() : null;
        if (appExt != null) {
            populateFirstPartyDataAttributes(appExt.getData(), result);
        }
    }

    private void mergeFirstPartyDataFromImp(Imp imp,
                                            ExtImpRubicon rubiconImpExt,
                                            ObjectNode result) {

        mergeFirstPartyDataFromData(imp, result);
        mergeFirstPartyDataKeywords(imp, result);
        // merge OPENRTB.imp[].ext.rubicon.keywords to XAPI.imp[].ext.rp.target.keywords
        mergeCollectionAttributeIntoArray(result, rubiconImpExt, ExtImpRubicon::getKeywords, FPD_KEYWORDS_FIELD);
    }

    private void mergeFirstPartyDataFromData(Imp imp, ObjectNode result) {
        final ObjectNode dataNode = toObjectNode(imp.getExt().get(FPD_DATA_FIELD));
        // merge OPENRTB.imp[].ext.data.* to XAPI.imp[].ext.rp.target.*
        populateFirstPartyDataAttributes(dataNode, result);

        // override XAPI.imp[].ext.rp.target.* with OPENRTB.imp[].ext.data.*
        overrideFirstPartyDataAttributes(dataNode, result);
    }

    private void overrideFirstPartyDataAttributes(ObjectNode dataNode, ObjectNode result) {
        final JsonNode pbadslotNode = dataNode.get(FPD_DATA_PBADSLOT_FIELD);
        if (pbadslotNode != null && pbadslotNode.isTextual()) {
            // copy imp[].ext.data.pbadslot to XAPI.imp[].ext.rp.target.pbadslot
            result.set(FPD_DATA_PBADSLOT_FIELD, pbadslotNode);
        } else {
            // copy adserver.adslot value to XAPI field imp[].ext.rp.target.dfp_ad_unit_code
            final String resolvedDfpAdUnitCode = getAdSlotFromAdServer(dataNode);
            if (resolvedDfpAdUnitCode != null) {
                result.set(DFP_ADUNIT_CODE_FIELD, TextNode.valueOf(resolvedDfpAdUnitCode));
            }
        }

    }

    private void mergeFirstPartyDataKeywords(Imp imp, ObjectNode result) {
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
            } else if (isIntegerArray(currentField)) {
                mergeIntoArray(targetNode, currentFieldName, integerArrayToStringList(currentField));
            }
        }
    }

    private static boolean isTextualArray(JsonNode node) {
        return node.isArray() && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isTextual);
    }

    private static boolean isBooleanArray(JsonNode node) {
        return node.isArray() && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isBoolean);
    }

    private static boolean isIntegerArray(JsonNode node) {
        return node.isArray() && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isIntegralNumber);
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
                .toList();
    }

    private static List<String> integerArrayToStringList(JsonNode booleanArray) {
        return StreamSupport.stream(booleanArray.spliterator(), false)
                .map(JsonNode::intValue)
                .map(value -> Integer.toString(value))
                .toList();
    }

    private List<String> mapVendorsNamesToUrls(List<Metric> metrics) {
        if (metrics == null) {
            return null;
        }
        final List<String> vendorsUrls = metrics.stream()
                .filter(this::isMetricSupported)
                .map(metric -> ViewabilityVendors.valueOf(metric.getVendor()).getUrl())
                .toList();
        return vendorsUrls.isEmpty() ? null : vendorsUrls;
    }

    private Integer getMaxBids(ExtRequest extRequest) {
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final List<ExtRequestPrebidMultiBid> multibids = extRequestPrebid != null
                ? extRequestPrebid.getMultibid() : null;
        final ExtRequestPrebidMultiBid extRequestPrebidMultiBid =
                CollectionUtils.isNotEmpty(multibids) ? multibids.getFirst() : null;
        final Integer multibidMaxBids = extRequestPrebidMultiBid != null ? extRequestPrebidMultiBid.getMaxBids() : null;

        return multibidMaxBids != null ? multibidMaxBids : 1;
    }

    private String getGpid(ObjectNode impExt) {
        final JsonNode gpidNode = impExt.get(FPD_GPID_FIELD);
        return gpidNode != null && gpidNode.isTextual() ? gpidNode.asText() : null;
    }

    private ObjectNode getSkadn(ObjectNode impExt) {
        final JsonNode skadnNode = impExt.get(FPD_SKADN_FIELD);
        return skadnNode != null && skadnNode.isObject() ? (ObjectNode) skadnNode : null;
    }

    private String getTid(ObjectNode impExt) {
        final JsonNode tidNode = impExt.get(TID_FIELD);
        return tidNode != null && tidNode.isTextual() ? tidNode.asText() : null;
    }

    private String getAdSlot(Imp imp) {
        final ObjectNode dataNode = toObjectNode(imp.getExt().get(FPD_DATA_FIELD));

        return getAdSlotFromAdServer(dataNode);
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

    private static ImpMediaType impType(Imp imp) {
        final Video video = imp.getVideo();
        final Banner banner = imp.getBanner();
        final Native xNative = imp.getXNative();
        if (video != null && ((banner == null && xNative == null) || isFullyPopulatedVideo(video))) {
            return ImpMediaType.video;
        }
        if (banner == null && xNative != null) {
            return ImpMediaType.xNative;
        }

        return ImpMediaType.banner;
    }

    private static boolean isFullyPopulatedVideo(Video video) {
        // These are just recommended video fields for XAPI
        return video.getMimes() != null
                && video.getProtocols() != null
                && video.getMaxduration() != null
                && video.getLinearity() != null;
    }

    private static String referer(Site site) {
        return site != null ? site.getPage() : null;
    }

    private Video makeVideo(Imp imp, RubiconVideoParams rubiconVideoParams, String referer) {
        final Video video = imp.getVideo();

        final Integer skip = rubiconVideoParams != null ? rubiconVideoParams.getSkip() : null;
        final Integer skipDelay = rubiconVideoParams != null ? rubiconVideoParams.getSkipdelay() : null;
        final Integer resolvedSizeId = resolveSizeId(rubiconVideoParams, imp, referer);

        final Integer rewarded = imp.getRwdd();
        final String videoType = rewarded != null && rewarded == 1 ? "rewarded" : null;

        // optimization for empty ext params
        if (skip == null && skipDelay == null && resolvedSizeId == null && videoType == null) {
            return video;
        }

        return video.toBuilder()
                .ext(mapper.mapper().valueToTree(
                        RubiconVideoExt.of(skip,
                                skipDelay,
                                resolvedSizeId != null ? RubiconVideoExtRp.of(resolvedSizeId) : null,
                                videoType)))
                .build();
    }

    private Integer resolveSizeId(RubiconVideoParams rubiconVideoParams, Imp imp, String referer) {
        final Integer sizeId = rubiconVideoParams != null ? rubiconVideoParams.getSizeId() : null;
        final Integer resolvedSizeId = BidderUtil.isNullOrZero(sizeId)
                ? null
                : sizeId;
        validateVideoSizeId(resolvedSizeId, referer, imp.getId());

        return resolvedSizeId;
    }

    private static void validateVideoSizeId(Integer resolvedSizeId, String referer, String impId) {
        // log only 1% of cases to monitor how often video impressions does not have size id
        if (resolvedSizeId == null) {
            MISSING_VIDEO_SIZE_LOGGER.warn(
                    "RP adapter: video request with no size_id. Referrer URL = %s, impId = %s"
                            .formatted(referer, impId),
                    0.01d);
        }
    }

    private Banner makeBanner(Imp imp) {
        final Banner banner = imp.getBanner();
        final Integer width = banner.getW();
        final Integer height = banner.getH();
        final List<Format> format = banner.getFormat();
        if ((width == null || height == null) && CollectionUtils.isEmpty(format)) {
            throw new PreBidException("rubicon imps must have at least one size element [w, h, format]");
        }

        return banner.toBuilder()
                .ext(mapper.mapper().valueToTree(
                        RubiconBannerExt.of(RubiconBannerExtRp.of("text/html"))))
                .build();
    }

    private Native makeNative(Imp imp) {
        final Native xNative = imp.getXNative();
        final String version = ObjectUtil.getIfNotNull(xNative, Native::getVer);
        if (StringUtils.equalsAny(version, "1.0", "1.1")) {
            return xNative;
        }
        final String nativeRequest = xNative.getRequest();
        final JsonNode requestNode = nodeFromString(nativeRequest);

        try {
            validateNativeRequest(requestNode);
        } catch (PreBidException e) {
            throw new PreBidException(
                    String.format("Error in native object for imp with id %s: %s", imp.getId(), e.getMessage()));
        }

        return RubiconNative.builder()
                .requestNative((ObjectNode) requestNode)
                .request(xNative.getRequest())
                .ver(xNative.getVer())
                .api(xNative.getApi())
                .battr(xNative.getBattr())
                .ext(xNative.getExt())
                .build();
    }

    public final JsonNode nodeFromString(String stringValue) {
        try {
            return StringUtils.isNotBlank(stringValue) ? mapper.mapper().readTree(stringValue) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateNativeRequest(JsonNode requestNode) {
        if (requestNode == null) {
            throw new PreBidException("Request can not be parsed");
        }

        final JsonNode eventtrackers = requestNode.get("eventtrackers");
        if (eventtrackers == null || !eventtrackers.isArray()) {
            throw new PreBidException("Eventtrackers are not present or not of array type");
        }

        final JsonNode context = requestNode.get("context");
        if (context != null && !context.isInt()) {
            throw new PreBidException("Context is not of int type");
        }

        final JsonNode placement = requestNode.get("plcmttype");
        if (placement == null || !placement.isInt()) {
            throw new PreBidException("Plcmttype is not present or not of int type");
        }
    }

    private User makeUser(User user, ExtImpRubicon rubiconImpExt) {
        final String userId = user != null ? user.getId() : null;
        final List<Eid> userEids = user != null ? user.getEids() : null;
        final String resolvedId = userId == null ? resolveUserId(userEids) : null;
        final ExtUser extUser = user != null ? user.getExt() : null;
        final boolean hasStypeToRemove = hasStypeToRemove(userEids);
        final List<Eid> resolvedUserEids = hasStypeToRemove
                ? prepareUserEids(userEids)
                : userEids;
        final boolean hasDataToRemove = ObjectUtil.getIfNotNull(user, User::getData) != null;
        final RubiconUserExtRp userExtRp = rubiconUserExtRp(user, rubiconImpExt);
        final ObjectNode userExtData = extUser != null ? extUser.getData() : null;

        if (userExtRp == null
                && userExtData == null
                && resolvedUserEids == null
                && resolvedId == null
                && !hasStypeToRemove) {

            return hasDataToRemove
                    ? user.toBuilder().data(null).build()
                    : user;
        }

        final ExtUser userExt = ExtUser.builder()
                .eids(resolvedUserEids)
                .build();

        final RubiconUserExt rubiconUserExt = RubiconUserExt.builder()
                .rp(userExtRp)
                .build();

        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();

        return userBuilder
                .id(ObjectUtils.defaultIfNull(resolvedId, userId))
                .gender(null)
                .yob(null)
                .geo(null)
                .data(null)
                .eids(null)
                .ext(mapper.fillExtension(userExt, rubiconUserExt))
                .build();
    }

    // TODO: Refactor this
    private User downgradeUserConsent(User user) {
        if (user == null || user.getConsent() == null) {
            return user;
        }

        final ExtUser extUser = user.getExt();

        final ExtUser newUserExt = Optional.ofNullable(extUser)
                .map(ExtUser::toBuilder)
                .orElseGet(ExtUser::builder)
                .consent(user.getConsent())
                .build();

        if (extUser != null) {
            newUserExt.addProperties(user.getExt().getProperties());
        }

        return user.toBuilder()
                .consent(null)
                .ext(newUserExt)
                .build();
    }

    private static String resolveUserId(List<Eid> userEids) {
        return CollectionUtils.emptyIfNull(userEids)
                .stream()
                .map(userEid -> getIdFromFirstUuidWithStypePpuid(userEid.getUids()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String getIdFromFirstUuidWithStypePpuid(List<Uid> extUserEidUids) {
        return CollectionUtils.emptyIfNull(extUserEidUids).stream()
                .filter(Objects::nonNull)
                .filter(extUserEidUid -> Objects.equals(PPUID_STYPE, getUserEidUidStype(extUserEidUid)))
                .map(Uid::getId)
                .findFirst()
                .orElse(null);
    }

    private static String getUserEidUidStype(Uid uid) {
        final ObjectNode uidExt = uid != null ? uid.getExt() : null;
        final JsonNode stype = uidExt != null ? uidExt.path(STYPE_FIELD) : null;
        return stype != null && !stype.isMissingNode()
                ? stype.asText()
                : null;
    }

    private static boolean hasStypeToRemove(List<Eid> userEids) {
        return CollectionUtils.emptyIfNull(userEids).stream()
                .filter(Objects::nonNull)
                .map(Eid::getUids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(RubiconBidder::getUserEidUidStype)
                .filter(Objects::nonNull)
                .anyMatch(STYPE_TO_REMOVE::contains);
    }

    private static List<Eid> prepareUserEids(List<Eid> userEids) {
        return CollectionUtils.emptyIfNull(userEids).stream()
                .filter(Objects::nonNull)
                .map(RubiconBidder::prepareExtUserEid)
                .toList();
    }

    private static Eid prepareExtUserEid(Eid extUserEid) {
        final List<Uid> extUserEidUids = CollectionUtils.emptyIfNull(extUserEid.getUids()).stream()
                .filter(Objects::nonNull)
                .map(RubiconBidder::cleanExtUserEidUidStype)
                .toList();
        return extUserEid.toBuilder().uids(extUserEidUids).build();
    }

    private static Uid cleanExtUserEidUidStype(Uid extUserEidUid) {
        final ObjectNode extUserEidUidExt = extUserEidUid.getExt();
        if (extUserEidUidExt == null || !STYPE_TO_REMOVE.contains(getUserEidUidStype(extUserEidUid))) {
            return extUserEidUid;
        }

        final ObjectNode extUserEidUidExtCopy = extUserEidUidExt.deepCopy();
        extUserEidUidExtCopy.remove(STYPE_FIELD);

        return extUserEidUid.toBuilder().ext(extUserEidUidExtCopy).build();
    }

    private RubiconUserExtRp rubiconUserExtRp(User user, ExtImpRubicon rubiconImpExt) {
        final JsonNode target = rubiconUserExtRpTarget(rubiconImpExt.getVisitor(), user);

        return target != null ? RubiconUserExtRp.of(target) : null;
    }

    private JsonNode rubiconUserExtRpTarget(ObjectNode visitor, User user) {
        final ObjectNode result = existingRubiconUserExtRpTarget(user);

        populateFirstPartyDataAttributes(visitor, result);

        if (user != null) {
            mergeFirstPartyDataFromUser(user.getExt(), result);

            enrichWithIabAndSegtaxAttribute(result, user.getData(), USER_SEGTAXES);
        }

        return !result.isEmpty() ? result : null;
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

    private void mergeFirstPartyDataFromUser(ExtUser userExt, ObjectNode result) {
        // merge OPENRTB.user.ext.data.* to XAPI.user.ext.rp.target.*
        if (userExt != null) {
            populateFirstPartyDataAttributes(userExt.getData(), result);
        }
    }

    private static void enrichWithIabAndSegtaxAttribute(ObjectNode target, List<Data> data, Set<Integer> iabTaxes) {
        CollectionUtils.emptyIfNull(data).stream()
                .filter(Objects::nonNull)
                .flatMap(dataEntry -> extractTaxToSegmentId(dataEntry, iabTaxes))
                .limit(MAX_NUMBER_OF_SEGMENTS)
                .forEach(entry -> getArrayNodeOrCreate(target, entry.getKey()).add(entry.getValue()));
    }

    private static Stream<Map.Entry<String, String>> extractTaxToSegmentId(Data data, Set<Integer> iabTaxes) {
        final ObjectNode ext = data.getExt();
        final JsonNode taxonomyId = ext != null ? ext.get(SEGTAX) : null;
        if (taxonomyId == null || !taxonomyId.isInt()) {
            return Stream.empty();
        }

        final String taxKey = resolveTaxName(taxonomyId.intValue(), iabTaxes);
        return CollectionUtils.emptyIfNull(data.getSegment()).stream()
                .filter(Objects::nonNull)
                .map(Segment::getId)
                .filter(StringUtils::isNotBlank)
                .map(id -> Map.entry(taxKey, id));
    }

    private static String resolveTaxName(Integer taxonomyId, Set<Integer> iabTaxes) {
        return iabTaxes.contains(taxonomyId) ? SEGTAX_IAB : SEGTAX_TAX + taxonomyId;
    }

    private static ArrayNode getArrayNodeOrCreate(ObjectNode parent, String field) {
        final JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) {
            return parent.putArray(field);
        }

        return (ArrayNode) node;
    }

    private void processWarnings(List<BidderError> errors, List<String> priceFloorsWarnings) {
        if (CollectionUtils.isNotEmpty(priceFloorsWarnings)) {
            priceFloorsWarnings.forEach(warning -> errors.add(BidderError.badInput(warning)));
        }
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
        final boolean hasDataToRemove = ObjectUtil.getIfNotNull(siteContent, Content::getData) != null;

        final String contentLanguage = ObjectUtil.getIfNotNull(siteContent, Content::getLanguage);
        final String resolvedLanguage = StringUtils.isBlank(contentLanguage) && StringUtils.isNotBlank(impLanguage)
                ? impLanguage
                : null;

        return resolvedLanguage != null || hasDataToRemove
                ? Optional.ofNullable(siteContent)
                .map(Content::toBuilder)
                .orElseGet(Content::builder)
                .data(null)
                .language(resolvedLanguage != null ? resolvedLanguage : contentLanguage)
                .build()
                : siteContent;
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
            enrichWithIabAndSegtaxAttribute(target, siteContentData, SITE_SEGTAXES);
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

    private static Source makeSource(Source source) {
        final SupplyChain supplyChain = source != null ? source.getSchain() : null;
        if (supplyChain == null) {
            return source;
        }

        final ExtSource extSource = source.getExt();
        final ExtSource resolvedExtSource = copyProperties(extSource, ExtSource.of(supplyChain));

        return source.toBuilder()
                .schain(null)
                .ext(resolvedExtSource)
                .build();
    }

    private static <T extends FlexibleExtension> T copyProperties(T source, T target) {
        Optional.ofNullable(source)
                .map(FlexibleExtension::getProperties)
                .ifPresent(target::addProperties);

        return target;
    }

    private static Regs makeRegs(Regs regs) {
        if (regs == null) {
            return null;
        }

        final Integer gdpr = regs.getGdpr();
        final String usPrivacy = regs.getUsPrivacy();
        if (gdpr == null && usPrivacy == null) {
            return regs;
        }

        final ExtRegs originalExtRegs = regs.getExt();
        final String gpc = originalExtRegs != null ? originalExtRegs.getGpc() : null;
        final ExtRegsDsa dsa = originalExtRegs != null ? originalExtRegs.getDsa() : null;
        final ExtRegs extRegs = copyProperties(originalExtRegs, ExtRegs.of(gdpr, usPrivacy, gpc, dsa));

        return regs.toBuilder()
                .gdpr(null)
                .usPrivacy(null)
                .ext(extRegs)
                .build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, String uri) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .body(mapper.encodeToBytes(bidRequest))
                .headers(headers)
                .payload(bidRequest)
                .impIds(BidderUtil.impIds(bidRequest))
                .build();
    }

    private static boolean hasDeals(Imp imp) {
        return imp.getPmp() != null && CollectionUtils.isNotEmpty(imp.getPmp().getDeals());
    }

    private List<HttpRequest<BidRequest>> createDealsRequests(BidRequest bidRequest, String uri) {
        final Imp singleImp = bidRequest.getImp().getFirst();
        return singleImp.getPmp().getDeals().stream()
                .map(deal -> mapper.mapper().convertValue(deal.getExt(), ExtDeal.class))
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .filter(Objects::nonNull)
                .map(lineItem -> createLineItemBidRequest(lineItem, bidRequest, singleImp))
                .map((BidRequest request) -> createHttpRequest(request, uri))
                .toList();
    }

    private BidRequest createLineItemBidRequest(ExtDealLine lineItem, BidRequest bidRequest, Imp imp) {
        final Imp dealsImp = imp.toBuilder()
                .banner(modifyBanner(imp.getBanner(), lineItem.getSizes()))
                .ext(modifyRubiconImpExt(imp.getExt(), bidRequest.getExt(), lineItem.getExtLineItemId(),
                        getAdSlot(imp)))
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
        final RubiconImpExtRp modifiedImpExtRp = RubiconImpExtRp.of(
                impExtRp.getZoneId(),
                modifiedTargetNode,
                impExtRp.getTrack(),
                impExtRp.getRtb(),
                impExtRp.getPbBidId());

        return mapper.mapper().valueToTree(rubiconImpExt.toBuilder()
                .rp(modifiedImpExtRp)
                .maxbids(getMaxBids(extRequest))
                .gpid(adSlot)
                .prebid(null)
                .build());
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
        final Map<String, Imp> idToRubiconImp = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        final RubiconExtPrebidBiddersBidder extPrebidBiddersBidder = extPrebidBiddersRubicon(prebidRequest.getExt());
        final Float cpmOverrideFromRequest = cpmOverrideFromRequest(extPrebidBiddersBidder);
        final boolean hasApexRenderer = hasApexRenderer(extPrebidBiddersBidder);
        final BidType bidType = bidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .map(bid -> updateBid(
                                bid,
                                seatBid,
                                idToImp.get(bid.getImpid()),
                                idToRubiconImp.get(bid.getImpid()),
                                bidType,
                                cpmOverrideFromRequest,
                                hasApexRenderer,
                                errors))
                        .filter(Objects::nonNull)
                        .map(bid -> createBidderBid(
                                bid,
                                idToRubiconImp.get(bid.getImpid()),
                                bidType,
                                bidResponse.getCur()))
                        .toList())
                .flatMap(Collection::stream)
                .toList();
    }

    private Bid updateBid(RubiconBid bid,
                          RubiconSeatBid seatBid,
                          Imp imp,
                          Imp rubiconImp,
                          BidType bidType,
                          Float cpmOverrideFromRequest,
                          boolean hasApexRenderer,
                          List<BidderError> errors) {

        final ObjectNode updateBidExt;
        try {
            updateBidExt = prepareBidExt(bid, seatBid, imp, bidType, hasApexRenderer);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        // Unconditionally set price if coming from CPM override
        final Float cpmOverride = ObjectUtils.defaultIfNull(cpmOverrideFromImp(imp), cpmOverrideFromRequest);
        final BigDecimal bidPrice = cpmOverride != null
                ? new BigDecimal(String.valueOf(cpmOverride))
                : bid.getPrice();

        final RubiconBid updatedRubiconBid = bid.toBuilder()
                .id(resolveBidId(rubiconImp, bid))
                .adm(resolveAdm(bid.getAdm(), bid.getAdmNative()))
                .price(bidPrice)
                .ext(updateBidExt)
                .build();

        return bidFromRubiconBid(updatedRubiconBid);
    }

    private ObjectNode prepareBidExt(RubiconBid bid,
                                     RubiconSeatBid seatBid,
                                     Imp imp,
                                     BidType bidType,
                                     boolean hasApexRenderer) {

        final ObjectNode bidExt = bid.getExt();
        final ExtPrebid<ExtBidPrebid, ObjectNode> extPrebid = getExtPrebid(bidExt, bid.getId());
        final ExtBidPrebid extBidPrebid = extPrebid != null ? extPrebid.getPrebid() : null;
        final ExtBidPrebidMeta meta = extBidPrebid != null ? extBidPrebid.getMeta() : null;

        final Integer networkId = resolveNetworkId(seatBid);
        final String seat = seatBid.getSeat();
        final String rendererUrl = resolveRendererUrl(imp, meta, bidType, hasApexRenderer);

        if (ObjectUtils.allNull(networkId, rendererUrl, seat)) {
            return bidExt;
        }

        final ExtBidPrebidMeta updatedMeta = Optional.ofNullable(meta)
                .map(ExtBidPrebidMeta::toBuilder)
                .orElseGet(ExtBidPrebidMeta::builder)
                .networkId(networkId)
                .seat(seat)
                .rendererUrl(rendererUrl)
                .build();

        final ExtBidPrebid modifiedExtBidPrebid = extBidPrebid != null
                ? extBidPrebid.toBuilder().meta(updatedMeta).build()
                : ExtBidPrebid.builder().meta(updatedMeta).build();

        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();
        updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(modifiedExtBidPrebid));

        return updatedBidExt;
    }

    private ExtPrebid<ExtBidPrebid, ObjectNode> getExtPrebid(ObjectNode bidExt, String bidId) {
        try {
            return bidExt != null ? mapper.mapper().convertValue(bidExt, EXT_PREBID_TYPE_REFERENCE) : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext passed in bid with id: " + bidId);
        }
    }

    private static Integer resolveNetworkId(RubiconSeatBid seatBid) {
        final String buyer = seatBid.getBuyer();
        final int networkId = NumberUtils.toInt(buyer, 0);
        return networkId <= 0 ? null : networkId;
    }

    private String resolveRendererUrl(Imp imp, ExtBidPrebidMeta meta, BidType bidType, boolean hasApexRenderer) {
        if (imp == null) {
            return null;
        }

        final Video video = imp.getVideo();
        return hasApexRenderer
                && (bidType == BidType.video || isVideoMetaMediaType(meta))
                && (video != null && !Objects.equals(video.getPlacement(), 1) && !Objects.equals(video.getPlcmt(), 1))
                ? apexRendererUrl
                : null;
    }

    private static Boolean isVideoMetaMediaType(ExtBidPrebidMeta meta) {
        return Optional.ofNullable(meta)
                .map(ExtBidPrebidMeta::getMediaType)
                .map("video"::equalsIgnoreCase)
                .orElse(false);
    }

    private String resolveBidId(Imp rubiconImp, RubiconBid bid) {
        return generateBidId
                ? Optional.ofNullable(rubiconImp)
                .map(Imp::getExt)
                .map(ext -> ext.get("rp"))
                .map(rp -> rp.get("pb_bid_id"))
                .map(JsonNode::asText)
                .orElse(bid.getId())
                : bid.getId();
    }

    private String resolveAdm(String bidAdm, ObjectNode admobject) {
        if (StringUtils.isNotBlank(bidAdm)) {
            return bidAdm;
        }

        return admobject != null ? admobject.toString() : null;
    }

    private Bid bidFromRubiconBid(RubiconBid rubiconBid) {
        try {
            return mapper.mapper().convertValue(rubiconBid, Bid.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error converting rubiconBid to  ortbBid: " + e.getMessage(), e);
        }
    }

    private static BidderBid createBidderBid(Bid bid, Imp imp, BidType bidType, String currency) {
        return BidderBid.builder()
                .bid(bid)
                .type(bidType)
                .bidCurrency(currency)
                .priceFloorInfo(imp != null ? PriceFloorInfo.of(imp.getBidfloor(), imp.getBidfloorcur()) : null)
                .build();
    }

    private static Float cpmOverrideFromRequest(RubiconExtPrebidBiddersBidder bidder) {
        final RubiconExtPrebidBiddersBidderDebug debug = bidder != null ? bidder.getDebug() : null;
        return debug != null ? debug.getCpmoverride() : null;
    }

    private Float cpmOverrideFromImp(Imp imp) {
        return Optional.ofNullable(imp)
                .map(this::parseRubiconExt)
                .map(ExtImpRubicon::getDebug)
                .map(ExtImpRubiconDebug::getCpmoverride)
                .orElse(null);
    }

    private static boolean hasApexRenderer(RubiconExtPrebidBiddersBidder bidder) {
        return Optional.ofNullable(bidder).map(RubiconExtPrebidBiddersBidder::getApexRenderer).orElse(false);
    }

    private static BidType bidType(BidRequest bidRequest) {
        final ImpMediaType impMediaType = impType(bidRequest.getImp().getFirst());
        return switch (impMediaType) {
            case video -> BidType.video;
            case banner -> BidType.banner;
            case xNative -> BidType.xNative;
            default -> throw new PreBidException("Unsupported bid mediaType");
        };
    }

    private ObjectNode toObjectNode(JsonNode node) {
        return node != null && node.isObject()
                ? (ObjectNode) node
                : mapper.mapper().createObjectNode();
    }
}
