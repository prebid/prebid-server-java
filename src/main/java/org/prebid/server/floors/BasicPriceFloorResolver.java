package org.prebid.server.floors;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.DeviceType;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorField;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.IterableUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.dsl.config.PrebidConfigMatchingStrategy;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;
import org.prebid.server.util.dsl.config.PrebidConfigSource;
import org.prebid.server.util.dsl.config.impl.MostAccurateCombinationStrategy;
import org.prebid.server.util.dsl.config.impl.SimpleDirectParameter;
import org.prebid.server.util.dsl.config.impl.SimpleParameters;
import org.prebid.server.util.dsl.config.impl.SimpleSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BasicPriceFloorResolver implements PriceFloorResolver {

    private static final Logger logger = LoggerFactory.getLogger(BasicPriceFloorResolver.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String DEFAULT_RULES_CURRENCY = "USD";
    private static final String SCHEMA_DEFAULT_DELIMITER = "|";
    private static final String WILDCARD_CATCH_ALL = "*";

    private static final String VIDEO_ALIAS = "video-instream";

    private static final JsonPointer ADSERVER_NAME_POINTER = JsonPointer.valueOf("/data/adserver/name");
    private static final JsonPointer ADSLOT_POINTER = JsonPointer.valueOf("/data/adserver/adslot");
    private static final JsonPointer PB_ADSLOT_POINTER = JsonPointer.valueOf("/data/pbadslot");
    private static final JsonPointer GPID_POINTER = JsonPointer.valueOf("/gpid");
    private static final JsonPointer PBADSLOT_POINTER = JsonPointer.valueOf("/data/pbadslot");
    private static final JsonPointer STORED_REQUEST_ID_POINTER = JsonPointer.valueOf("/prebid/storedrequest/id");

    private static final List<Pattern> PHONE_PATTERNS =
            createPatterns("Phone", "iPhone", "Android.*Mobile", "Mobile.*Android");
    private static final List<Pattern> TABLET_PATTERNS =
            createPatterns("tablet", "iPad", "Windows NT.*touch", "touch.*Windows NT", "Android");

    private final CurrencyConversionService currencyConversionService;
    private final CountryCodeMapper countryCodeMapper;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    private final PrebidConfigMatchingStrategy matchingStrategy;

    public BasicPriceFloorResolver(CurrencyConversionService currencyConversionService,
                                   CountryCodeMapper countryCodeMapper,
                                   Metrics metrics,
                                   JacksonMapper mapper) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);

        matchingStrategy = new MostAccurateCombinationStrategy();
    }

    @Override
    public PriceFloorResult resolve(BidRequest bidRequest,
                                    PriceFloorRules floorRules,
                                    Imp imp,
                                    ImpMediaType mediaType,
                                    Format format,
                                    List<String> warnings) {

        if (isPriceFloorsDisabledForRequest(bidRequest)) {
            return null;
        }

        final PriceFloorModelGroup modelGroup = extractFloorModelGroup(floorRules);

        if (modelGroup == null) {
            return null;
        }

        final PriceFloorSchema schema = modelGroup.getSchema();
        if (schema == null || CollectionUtils.isEmpty(schema.getFields())) {
            return null;
        }

        if (MapUtils.isEmpty(modelGroup.getValues())) {
            return null;
        }

        final Map<String, BigDecimal> values = keysToLowerCase(modelGroup.getValues());
        final PrebidConfigSource source = SimpleSource.of(
                WILDCARD_CATCH_ALL,
                ObjectUtils.defaultIfNull(schema.getDelimiter(), SCHEMA_DEFAULT_DELIMITER),
                values.keySet());
        final PrebidConfigParameters parameters = createParameters(schema, bidRequest, imp, mediaType, format);

        final String rule = matchingStrategy.match(source, parameters);
        final BigDecimal floorForRule = rule != null ? values.get(rule) : null;
        final BigDecimal floor = floorForRule != null ? floorForRule : modelGroup.getDefaultFloor();
        final String modelGroupCurrency = modelGroup.getCurrency();
        final String floorCurrency = StringUtils.isNotEmpty(modelGroupCurrency)
                ? modelGroupCurrency
                : getDataCurrency(floorRules);

        try {
            return resolveResult(floor, rule, floorForRule, imp, bidRequest, floorCurrency, warnings);
        } catch (PreBidException e) {
            final String logMessage = "Error occurred while resolving floor for imp: %s, cause: %s"
                    .formatted(imp.getId(), e.getMessage());
            if (warnings != null) {
                warnings.add(logMessage);
            }
            logger.debug(logMessage);
            conditionalLogger.error(logMessage, 0.01d);
            metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);
        }

        return null;
    }

    private static boolean isPriceFloorsDisabledForRequest(BidRequest bidRequest) {
        final PriceFloorRules requestFloors = extractRequestFloors(bidRequest);
        final Boolean enabled = ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getEnabled);
        final Boolean skipped = ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getSkipped);

        return BooleanUtils.isFalse(enabled) || BooleanUtils.isTrue(skipped);
    }

    private static PriceFloorRules extractRequestFloors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private static PriceFloorModelGroup extractFloorModelGroup(PriceFloorRules floors) {
        final PriceFloorData data = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final List<PriceFloorModelGroup> modelGroups = ObjectUtil.getIfNotNull(data, PriceFloorData::getModelGroups);

        return CollectionUtils.isNotEmpty(modelGroups) ? modelGroups.get(0) : null;
    }

    private static <V> Map<String, V> keysToLowerCase(Map<String, V> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
    }

    private PrebidConfigParameters createParameters(PriceFloorSchema schema,
                                                    BidRequest bidRequest,
                                                    Imp imp,
                                                    ImpMediaType mediaType,
                                                    Format format) {

        final List<ImpMediaType> resolvedMediaTypes = mediaType != null
                ? Collections.singletonList(mediaType)
                : mediaTypesFromImp(imp);

        final List<PrebidConfigParameter> conditionsMatchers = schema.getFields().stream()
                .map(field -> createParameter(field, bidRequest, imp, resolvedMediaTypes, format))
                .toList();

        return SimpleParameters.of(conditionsMatchers);
    }

    private static List<ImpMediaType> mediaTypesFromImp(Imp imp) {
        final List<ImpMediaType> impMediaTypes = new ArrayList<>();
        if (imp.getBanner() != null) {
            impMediaTypes.add(ImpMediaType.banner);
        }

        final Video video = imp.getVideo();
        if (video != null) {
            final Integer placement = video.getPlacement();
            if (placement == null || Objects.equals(placement, 1)) {
                impMediaTypes.add(ImpMediaType.video);
            } else {
                impMediaTypes.add(ImpMediaType.video_outstream);
            }
        }

        if (imp.getXNative() != null) {
            impMediaTypes.add(ImpMediaType.xNative);
        }

        if (imp.getAudio() != null) {
            impMediaTypes.add(ImpMediaType.audio);
        }

        return impMediaTypes;
    }

    private PrebidConfigParameter createParameter(PriceFloorField field,
                                                  BidRequest bidRequest,
                                                  Imp imp,
                                                  List<ImpMediaType> mediaTypes,
                                                  Format format) {

        return switch (field) {
            case siteDomain -> siteDomainFromRequest(bidRequest);
            case pubDomain -> pubDomainFromRequest(bidRequest);
            case domain -> domainFromRequest(bidRequest);
            case bundle -> bundleFromRequest(bidRequest);
            case channel -> channelFromRequest(bidRequest);
            case mediaType -> mediaTypeFrom(mediaTypes);
            case size -> sizeFrom(format, imp, mediaTypes);
            case gptSlot -> gptAdSlotFromImp(imp);
            case adUnitCode -> adUnitCodeFromImp(imp);
            case country -> countryFromRequest(bidRequest);
            case deviceType -> resolveDeviceTypeFromRequest(bidRequest);
        };
    }

    private static PrebidConfigParameter siteDomainFromRequest(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite()).map(Site::getDomain)
                .or(() -> Optional.ofNullable(bidRequest.getApp()).map(App::getDomain))
                .or(() -> Optional.ofNullable(bidRequest.getDooh()).map(Dooh::getDomain))
                .map(BasicPriceFloorResolver::parameter)
                .orElse(PrebidConfigParameter.wildcard());
    }

    private static PrebidConfigParameter pubDomainFromRequest(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite()).map(Site::getPublisher)
                .or(() -> Optional.ofNullable(bidRequest.getApp()).map(App::getPublisher))
                .or(() -> Optional.ofNullable(bidRequest.getDooh()).map(Dooh::getPublisher))
                .map(Publisher::getDomain)
                .map(BasicPriceFloorResolver::parameter)
                .orElse(PrebidConfigParameter.wildcard());
    }

    private static PrebidConfigParameter domainFromRequest(BidRequest bidRequest) {
        final PrebidConfigParameter siteDomain = siteDomainFromRequest(bidRequest);
        final PrebidConfigParameter pubDomain = pubDomainFromRequest(bidRequest);
        if (isWildcard(siteDomain)) {
            return pubDomain;
        } else if (isWildcard(pubDomain)) {
            return siteDomain;
        }

        return SimpleDirectParameter.of(IterableUtil.union(
                ((PrebidConfigParameter.Direct) siteDomain).values(),
                ((PrebidConfigParameter.Direct) pubDomain).values()));
    }

    private static boolean isWildcard(PrebidConfigParameter parameter) {
        return parameter instanceof PrebidConfigParameter.Indirect.Wildcard;
    }

    private static PrebidConfigParameter bundleFromRequest(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getApp())
                .map(App::getBundle)
                .map(BasicPriceFloorResolver::parameter)
                .orElse(PrebidConfigParameter.wildcard());
    }

    private static PrebidConfigParameter channelFromRequest(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .map(ExtRequestPrebidChannel::getName)
                .map(BasicPriceFloorResolver::parameter)
                .orElse(PrebidConfigParameter.wildcard());
    }

    private static PrebidConfigParameter mediaTypeFrom(List<ImpMediaType> impMediaTypes) {
        if (CollectionUtils.isEmpty(impMediaTypes) || CollectionUtils.size(impMediaTypes) > 1) {
            return PrebidConfigParameter.wildcard();
        }

        final ImpMediaType impMediaType = impMediaTypes.get(0);
        return impMediaType == ImpMediaType.video
                ? SimpleDirectParameter.of(List.of(impMediaType.toString(), VIDEO_ALIAS))
                : SimpleDirectParameter.of(impMediaType.toString());
    }

    private static PrebidConfigParameter sizeFrom(Format size, Imp imp, List<ImpMediaType> mediaTypes) {
        final Format resolvedSize = size != null ? size : resolveFormatFromImp(imp, mediaTypes);
        return resolvedSize != null
                ? SimpleDirectParameter.of("%dx%d".formatted(resolvedSize.getW(), resolvedSize.getH()))
                : PrebidConfigParameter.wildcard();
    }

    private static Format resolveFormatFromImp(Imp imp, List<ImpMediaType> mediaTypes) {
        if (CollectionUtils.isEmpty(mediaTypes) || CollectionUtils.size(mediaTypes) > 1) {
            return null;
        }

        return switch (mediaTypes.get(0)) {
            case banner -> resolveFormatFromBannerImp(imp);
            case video -> resolveFormatFromVideoImp(imp);
            default -> null;
        };
    }

    private static Format resolveFormatFromBannerImp(Imp imp) {
        final Banner banner = imp.getBanner();
        final List<Format> formats = ObjectUtil.getIfNotNull(banner, Banner::getFormat);

        return switch (CollectionUtils.size(formats)) {
            case 0 -> formatOf(
                    ObjectUtil.getIfNotNull(banner, Banner::getW),
                    ObjectUtil.getIfNotNull(banner, Banner::getH));
            case 1 -> formats.get(0);
            default -> null;
        };
    }

    private static Format resolveFormatFromVideoImp(Imp imp) {
        final Video video = imp.getVideo();
        return formatOf(
                ObjectUtil.getIfNotNull(video, Video::getW),
                ObjectUtil.getIfNotNull(video, Video::getH));
    }

    private static Format formatOf(Integer width, Integer height) {
        return width != null && height != null
                ? Format.builder().w(width).h(height).build()
                : null;
    }

    private static PrebidConfigParameter gptAdSlotFromImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        if (impExt == null) {
            return PrebidConfigParameter.wildcard();
        }

        final String adServerName = stringByPath(impExt, ADSERVER_NAME_POINTER);
        final String gptAdSlot = "gam".equals(adServerName)
                ? stringByPath(impExt, ADSLOT_POINTER)
                : stringByPath(impExt, PB_ADSLOT_POINTER);

        return wildcardIfBlank(gptAdSlot);
    }

    private static String stringByPath(ObjectNode node, JsonPointer pointer) {
        final JsonNode nodeAtPointer = node.at(pointer);
        return !nodeAtPointer.isMissingNode() ? nodeAtPointer.asText() : null;
    }

    private static PrebidConfigParameter wildcardIfBlank(String value) {
        return StringUtils.isNotBlank(value)
                ? parameter(value)
                : PrebidConfigParameter.wildcard();
    }

    private static PrebidConfigParameter adUnitCodeFromImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final String tagId = imp.getTagid();
        if (impExt == null) {
            return wildcardIfBlank(tagId);
        }

        final String gpid = stringByPath(impExt, GPID_POINTER);
        if (StringUtils.isNotBlank(gpid)) {
            return parameter(gpid);
        }

        if (StringUtils.isNotBlank(tagId)) {
            return parameter(tagId);
        }

        final String adSlot = stringByPath(impExt, PBADSLOT_POINTER);
        if (StringUtils.isNotBlank(adSlot)) {
            return parameter(adSlot);
        }

        return wildcardIfBlank(stringByPath(impExt, STORED_REQUEST_ID_POINTER));
    }

    private PrebidConfigParameter countryFromRequest(BidRequest bidRequest) {
        final Optional<String> country = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getGeo)
                .map(Geo::getCountry);

        return country
                .filter(StringUtils::isNotBlank)
                .map(countryCodeMapper::mapToAlpha3)
                .or(() -> country)
                .map(BasicPriceFloorResolver::parameter)
                .orElse(PrebidConfigParameter.wildcard());
    }

    public static PrebidConfigParameter resolveDeviceTypeFromRequest(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final String userAgent = ObjectUtil.getIfNotNull(device, Device::getUa);

        if (StringUtils.isBlank(userAgent)) {
            return PrebidConfigParameter.wildcard();
        }

        for (Pattern pattern : PHONE_PATTERNS) {
            if (pattern.matcher(userAgent).matches()) {
                return SimpleDirectParameter.of(DeviceType.phone.name());
            }
        }

        for (Pattern pattern : TABLET_PATTERNS) {
            if (pattern.matcher(userAgent).matches()) {
                return SimpleDirectParameter.of(DeviceType.tablet.name());
            }
        }

        return SimpleDirectParameter.of(DeviceType.desktop.name());
    }

    private static String getDataCurrency(PriceFloorRules rules) {
        final PriceFloorData data = ObjectUtil.getIfNotNull(rules, PriceFloorRules::getData);
        return ObjectUtil.getIfNotNull(data, PriceFloorData::getCurrency);
    }

    private PriceFloorResult resolveResult(BigDecimal floor,
                                           String rule,
                                           BigDecimal floorForRule,
                                           Imp imp,
                                           BidRequest bidRequest,
                                           String rulesCurrency,
                                           List<String> warnings) {

        if (floor == null) {
            return null;
        }

        final Price floorMinValues = resolveFloorMin(bidRequest, imp, warnings);
        final BigDecimal floorMin = floorMinValues.getValue();
        final String floorMinCur = floorMinValues.getCurrency();

        final String effectiveRulesCurrency = ObjectUtils.defaultIfNull(rulesCurrency, DEFAULT_RULES_CURRENCY);
        final String effectiveFloorMinCurrency =
                ObjectUtils.firstNonNull(floorMinCur, rulesCurrency, DEFAULT_RULES_CURRENCY);

        final BigDecimal convertedFloorMinValue = !StringUtils.equals(effectiveRulesCurrency, effectiveFloorMinCurrency)
                ? currencyConversionService.convertCurrency(
                floorMin,
                bidRequest,
                effectiveFloorMinCurrency,
                effectiveRulesCurrency)
                : null;

        final Price effectiveFloor = Price.of(effectiveRulesCurrency, floor);
        final Price effectiveFloorMin = Price.of(effectiveFloorMinCurrency, floorMin);
        final Price convertedFloorMin = convertedFloorMinValue != null
                ? Price.of(effectiveRulesCurrency, convertedFloorMinValue)
                : Price.of(effectiveFloorMinCurrency, floorMin);

        final Price resolvedPrice = resolvePrice(effectiveFloor, convertedFloorMin, effectiveFloorMin);
        return PriceFloorResult.of(
                rule,
                floorForRule,
                ObjectUtil.getIfNotNull(resolvedPrice, Price::getValue),
                ObjectUtil.getIfNotNull(resolvedPrice, Price::getCurrency));
    }

    private Price resolveFloorMin(BidRequest bidRequest, Imp imp, List<String> warnings) {
        final Optional<ExtImpPrebidFloors> extImpPrebidFloors = Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .map(ExtImpPrebid::getFloors);
        final BigDecimal impFloorMin = extImpPrebidFloors
                .map(ExtImpPrebidFloors::getFloorMin)
                .orElse(null);
        final String impFloorMinCur = extImpPrebidFloors
                .map(ExtImpPrebidFloors::getFloorMinCur)
                .orElse(null);

        final Optional<PriceFloorRules> floorRules = extractRules(bidRequest);
        final BigDecimal requestFloorMin = floorRules.map(PriceFloorRules::getFloorMin).orElse(null);
        final String requestFloorMinCur = floorRules.map(PriceFloorRules::getFloorMinCur).orElse(null);

        if (ObjectUtils.allNotNull(impFloorMinCur, requestFloorMinCur)
                && !impFloorMinCur.equals(requestFloorMinCur)) {
            warnings.add("imp[].ext.prebid.floors.floorMinCur and ext.prebid.floors.floorMinCur has different values");
        }

        return Price.of(
                ObjectUtils.defaultIfNull(impFloorMinCur, requestFloorMinCur),
                ObjectUtils.defaultIfNull(impFloorMin, requestFloorMin));
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return mapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private static Optional<PriceFloorRules> extractRules(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getFloors);
    }

    private static Price resolvePrice(Price floor, Price convertedFloorMin, Price floorMin) {
        final BigDecimal floorValue = ObjectUtil.getIfNotNull(floor, Price::getValue);
        final String floorCurrency = ObjectUtil.getIfNotNull(floor, Price::getCurrency);

        final BigDecimal floorMinValue = ObjectUtil.getIfNotNull(convertedFloorMin, Price::getValue);
        final String floorMinCurrency = ObjectUtil.getIfNotNull(floor, Price::getCurrency);

        if (StringUtils.equals(floorCurrency, floorMinCurrency) && floorValue != null && floorMinValue != null) {

            return floorValue.compareTo(floorMinValue) > 0
                    ? roundPrice(floor)
                    : roundPrice(convertedFloorMin);
        }

        return roundPrice(ObjectUtils.defaultIfNull(floor, floorMin));
    }

    private static Price roundPrice(Price price) {
        return price != null ? Price.of(price.getCurrency(), BidderUtil.roundFloor(price.getValue())) : null;
    }

    private static List<Pattern> createPatterns(String... regexes) {
        return Arrays.stream(regexes).map(Pattern::compile).toList();
    }

    private static PrebidConfigParameter parameter(String value) {
        return SimpleDirectParameter.of(value.toLowerCase());
    }
}
