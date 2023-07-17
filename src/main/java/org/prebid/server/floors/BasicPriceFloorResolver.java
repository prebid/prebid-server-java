package org.prebid.server.floors;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.CombinatoricsUtils;
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
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BasicPriceFloorResolver implements PriceFloorResolver {

    private static final Logger logger = LoggerFactory.getLogger(BasicPriceFloorResolver.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String DEFAULT_RULES_CURRENCY = "USD";
    private static final String SCHEMA_DEFAULT_DELIMITER = "|";
    private static final String WILDCARD_CATCH_ALL = "*";
    private static final String VIDEO_ALIAS = "video-instream";
    private static final JsonPointer PB_ADSLOT_POINTER = JsonPointer.valueOf("/data/pbadslot");
    private static final JsonPointer ADSLOT_POINTER = JsonPointer.valueOf("/data/adserver/adslot");
    private static final JsonPointer ADSERVER_NAME_POINTER = JsonPointer.valueOf("/data/adserver/name");

    private static final Set<String> PHONE_PATTERNS = Set.of("Phone", "iPhone", "Android.*Mobile", "Mobile.*Android");
    private static final Set<String> TABLET_PATTERNS = Set.of("tablet", "iPad", "Windows NT.*touch",
            "touch.*Windows NT", "Android");
    private static final String GPID_PATH = "/gpid";
    private static final String PBADSLOT_PATH = "/data/pbadslot";
    private static final String STORED_REQUEST_ID_PATH = "/prebid/storedrequest/id";

    private final CurrencyConversionService currencyConversionService;
    private final CountryCodeMapper countryCodeMapper;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    public BasicPriceFloorResolver(CurrencyConversionService currencyConversionService,
                                   CountryCodeMapper countryCodeMapper,
                                   Metrics metrics,
                                   JacksonMapper mapper) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
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

        final String delimiter = ObjectUtils.defaultIfNull(schema.getDelimiter(), SCHEMA_DEFAULT_DELIMITER);
        final List<List<String>> desiredRuleKey = createRuleKey(schema, bidRequest, imp, mediaType, format);

        final Map<String, BigDecimal> rules = keysToLowerCase(modelGroup.getValues());

        final String rule = findRule(rules, delimiter, desiredRuleKey);
        final BigDecimal floorForRule = rule != null ? rules.get(rule) : null;

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

    private List<List<String>> createRuleKey(PriceFloorSchema schema,
                                             BidRequest bidRequest,
                                             Imp imp,
                                             ImpMediaType mediaType,
                                             Format format) {

        return schema.getFields().stream()
                .map(field -> toFieldValues(field, bidRequest, imp, mediaType, format))
                .map(BasicPriceFloorResolver::prepareFieldValues)
                .toList();
    }

    private List<String> toFieldValues(PriceFloorField field,
                                       BidRequest bidRequest,
                                       Imp imp,
                                       ImpMediaType mediaType,
                                       Format format) {

        final List<ImpMediaType> resolvedMediaTypes = mediaType != null
                ? Collections.singletonList(mediaType)
                : mediaTypesFromImp(imp);

        return switch (field) {
            case siteDomain -> siteDomainFromRequest(bidRequest);
            case pubDomain -> pubDomainFromRequest(bidRequest);
            case domain -> domainFromRequest(bidRequest);
            case bundle -> bundleFromRequest(bidRequest);
            case channel -> channelFromRequest(bidRequest);
            case mediaType -> mediaTypeToRuleKey(resolvedMediaTypes);
            case size ->
                    sizeFromFormat(ObjectUtils.defaultIfNull(format, resolveFormatFromImp(imp, resolvedMediaTypes)));
            case gptSlot -> gptAdSlotFromImp(imp);
            case adUnitCode -> adUnitCodeFromImp(imp);
            case country -> countryFromRequest(bidRequest);
            case deviceType -> resolveDeviceTypeFromRequest(bidRequest);
        };
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

    private static List<String> siteDomainFromRequest(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        final String siteDomain = ObjectUtil.getIfNotNull(site, Site::getDomain);

        if (StringUtils.isNotEmpty(siteDomain)) {
            return Collections.singletonList(siteDomain);
        }

        final App app = bidRequest.getApp();
        final String appDomain = ObjectUtil.getIfNotNull(app, App::getDomain);

        return Collections.singletonList(appDomain);
    }

    private static List<String> pubDomainFromRequest(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        final Publisher sitePublisher = ObjectUtil.getIfNotNull(site, Site::getPublisher);
        final String sitePublisherDomain = ObjectUtil.getIfNotNull(sitePublisher, Publisher::getDomain);

        if (StringUtils.isNotEmpty(sitePublisherDomain)) {
            return Collections.singletonList(sitePublisherDomain);
        }

        final App app = bidRequest.getApp();
        final Publisher appPublisher = ObjectUtil.getIfNotNull(app, App::getPublisher);
        final String appPublisherDomain = ObjectUtil.getIfNotNull(appPublisher, Publisher::getDomain);

        return Collections.singletonList(appPublisherDomain);
    }

    private static List<String> domainFromRequest(BidRequest bidRequest) {
        return ListUtils.union(siteDomainFromRequest(bidRequest), pubDomainFromRequest(bidRequest));
    }

    private static List<String> bundleFromRequest(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final String appBundle = ObjectUtil.getIfNotNull(app, App::getBundle);

        return Collections.singletonList(appBundle);
    }

    private static List<String> channelFromRequest(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        final ExtRequestPrebidChannel channel = ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getChannel);
        final String channelName = ObjectUtil.getIfNotNull(channel, ExtRequestPrebidChannel::getName);

        return Collections.singletonList(channelName);
    }

    private static List<String> mediaTypeToRuleKey(List<ImpMediaType> impMediaTypes) {

        if (CollectionUtils.isEmpty(impMediaTypes) || CollectionUtils.size(impMediaTypes) > 1) {
            return Collections.singletonList(WILDCARD_CATCH_ALL);
        }

        final ImpMediaType impMediaType = impMediaTypes.get(0);

        if (impMediaType == ImpMediaType.video) {
            return List.of(impMediaType.toString(), VIDEO_ALIAS);
        }

        return Collections.singletonList(impMediaType.toString());
    }

    private static Format resolveFormatFromImp(Imp imp, List<ImpMediaType> mediaTypes) {
        if (CollectionUtils.isEmpty(mediaTypes) || CollectionUtils.size(mediaTypes) > 1) {
            return null;
        }

        final ImpMediaType mediaType = mediaTypes.get(0);
        if (mediaType == ImpMediaType.banner) {
            return resolveFormatFromBannerImp(imp);
        }
        if (mediaType == ImpMediaType.video) {
            return resolveFormatFromVideoImp(imp);
        }

        return null;
    }

    private static Format resolveFormatFromBannerImp(Imp imp) {
        final Banner banner = imp.getBanner();
        final List<Format> formats = ObjectUtil.getIfNotNull(banner, Banner::getFormat);

        final int formatsSize = CollectionUtils.size(formats);
        if (formatsSize == 1) {
            return formats.get(0);
        } else if (formatsSize > 1) {
            return null;
        }

        final Integer bannerWidth = ObjectUtil.getIfNotNull(banner, Banner::getW);
        final Integer bannerHeight = ObjectUtil.getIfNotNull(banner, Banner::getH);

        return ObjectUtils.allNotNull(bannerWidth, bannerHeight)
                ? Format.builder().w(bannerWidth).h(bannerHeight).build()
                : null;
    }

    private static Format resolveFormatFromVideoImp(Imp imp) {
        final Video video = imp.getVideo();

        final Integer videoWidth = ObjectUtil.getIfNotNull(video, Video::getW);
        final Integer videoHeight = ObjectUtil.getIfNotNull(video, Video::getH);

        return ObjectUtils.allNotNull(videoWidth, videoHeight)
                ? Format.builder().w(videoWidth).h(videoHeight).build()
                : null;
    }

    private static List<String> sizeFromFormat(Format size) {
        final String sizeRuleKey = size != null
                ? "%dx%d".formatted(size.getW(), size.getH())
                : WILDCARD_CATCH_ALL;

        return Collections.singletonList(sizeRuleKey);
    }

    private static List<String> gptAdSlotFromImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();

        if (impExt == null) {
            return Collections.singletonList(WILDCARD_CATCH_ALL);
        }

        final JsonNode adServerNameNode = impExt.at(ADSERVER_NAME_POINTER);
        final JsonNode adSlotNode = adServerNameNode.isTextual() && Objects.equals(adServerNameNode.asText(), "gam")
                ? impExt.at(ADSLOT_POINTER)
                : impExt.at(PB_ADSLOT_POINTER);
        final String gptAdSlot = !adSlotNode.isMissingNode() ? adSlotNode.asText() : null;

        return Collections.singletonList(gptAdSlot);
    }

    private static List<String> adUnitCodeFromImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final String tagId = imp.getTagid();
        if (impExt == null) {
            return catchAllIfBlank(tagId);
        }

        final String gpid = stringByPath(impExt, GPID_PATH);
        if (StringUtils.isNotBlank(gpid)) {
            return Collections.singletonList(gpid);
        }

        if (StringUtils.isNotBlank(tagId)) {
            return Collections.singletonList(tagId);
        }

        final String adSlot = stringByPath(impExt, PBADSLOT_PATH);
        if (StringUtils.isNotBlank(adSlot)) {
            return Collections.singletonList(adSlot);
        }

        final String storedRequestId = stringByPath(impExt, STORED_REQUEST_ID_PATH);

        return catchAllIfBlank(storedRequestId);
    }

    private static List<String> catchAllIfBlank(String value) {
        return StringUtils.isNotBlank(value)
                ? Collections.singletonList(value)
                : Collections.singletonList(WILDCARD_CATCH_ALL);
    }

    private static String stringByPath(ObjectNode node, String path) {
        final JsonNode gpidNode = node.at(path);
        return !gpidNode.isMissingNode() ? gpidNode.asText() : null;
    }

    private List<String> countryFromRequest(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final Geo geo = ObjectUtil.getIfNotNull(device, Device::getGeo);
        final String country = ObjectUtil.getIfNotNull(geo, Geo::getCountry);
        final String alpha3Code = StringUtils.isNotBlank(country) ? countryCodeMapper.mapToAlpha3(country) : null;
        final String countryRuleKey = StringUtils.isNotBlank(alpha3Code) ? alpha3Code : country;

        return Collections.singletonList(countryRuleKey);
    }

    public static List<String> resolveDeviceTypeFromRequest(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final String userAgent = ObjectUtil.getIfNotNull(device, Device::getUa);

        if (StringUtils.isBlank(userAgent)) {
            return Collections.singletonList(WILDCARD_CATCH_ALL);
        }

        for (String pattern : PHONE_PATTERNS) {
            if (userAgent.matches(pattern)) {
                return Collections.singletonList(DeviceType.phone.name());
            }
        }

        for (String pattern : TABLET_PATTERNS) {
            if (userAgent.matches(pattern)) {
                return Collections.singletonList(DeviceType.tablet.name());
            }
        }

        return Collections.singletonList(DeviceType.desktop.name());
    }

    private static List<String> prepareFieldValues(List<String> fieldValues) {
        final List<String> preparedFieldValues = CollectionUtils.emptyIfNull(fieldValues).stream()
                .filter(StringUtils::isNotEmpty)
                .map(String::toLowerCase)
                .toList();

        if (CollectionUtils.isEmpty(preparedFieldValues)) {
            return Collections.singletonList(WILDCARD_CATCH_ALL);
        }

        return preparedFieldValues;
    }

    private static String findRule(Map<String, BigDecimal> rules, String delimiter, List<List<String>> desiredRuleKey) {
        return RuleKeyCandidateIterator.from(desiredRuleKey, delimiter).asStream()
                .filter(rules::containsKey)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static <V> Map<String, V> keysToLowerCase(Map<String, V> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
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

    private static Price roundPrice(Price price) {
        return price != null ? Price.of(price.getCurrency(), BidderUtil.roundFloor(price.getValue())) : null;
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

    private static class RuleKeyCandidateIterator implements Iterator<String> {

        private final List<List<String>> desiredRuleKey;
        private final String delimiter;

        private int wildcardNum;
        private Iterator<String> currentIterator = null;
        private final List<Integer> implicitWildcardIndexes;

        private RuleKeyCandidateIterator(List<List<String>> desiredRuleKey, String delimiter) {
            this.desiredRuleKey = desiredRuleKey;
            this.delimiter = delimiter;

            implicitWildcardIndexes = findImplicitWildcards(desiredRuleKey);
            wildcardNum = implicitWildcardIndexes.size();
        }

        public static RuleKeyCandidateIterator from(List<List<String>> desiredRuleKey, String delimiter) {
            return new RuleKeyCandidateIterator(desiredRuleKey, delimiter);
        }

        @Override
        public boolean hasNext() {
            return wildcardNum <= desiredRuleKey.size();
        }

        @Override
        public String next() {
            if (currentIterator == null && wildcardNum <= desiredRuleKey.size()) {
                currentIterator = createIterator(wildcardNum, desiredRuleKey, delimiter);
            }

            if (currentIterator != null) {
                final String candidate = currentIterator.next();

                if (!currentIterator.hasNext()) {
                    currentIterator = null;
                    wildcardNum++;
                }

                return candidate;
            }

            throw new NoSuchElementException();
        }

        public Stream<String> asStream() {
            return asStream(this);
        }

        private static <T> Stream<T> asStream(Iterator<T> iterator) {
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
        }

        private static List<Integer> findImplicitWildcards(List<List<String>> desiredRuleKey) {
            return IntStream.range(0, desiredRuleKey.size())
                    .filter(i -> desiredRuleKey.get(i).get(0).equals(WILDCARD_CATCH_ALL))
                    .boxed()
                    .toList();
        }

        private Iterator<String> createIterator(int wildcardNum, List<List<String>> desiredRuleKey, String delimiter) {
            final int ruleSegmentsNum = desiredRuleKey.size();

            return asStream(CombinatoricsUtils.combinationsIterator(ruleSegmentsNum, wildcardNum))
                    .map(combination -> IntStream.of(combination).boxed().toList())
                    .filter(combination -> combination.containsAll(implicitWildcardIndexes))
                    .sorted(Comparator.comparingInt(combination -> calculateWeight(combination, ruleSegmentsNum)))
                    .flatMap(combination -> combinationToCandidate(combination, desiredRuleKey, delimiter).stream())
                    .iterator();
        }

        private static Integer calculateWeight(List<Integer> combination, int ruleSegmentsNum) {
            return combination.stream()
                    .mapToInt(i -> 1 << (ruleSegmentsNum - i))
                    .sum();
        }

        private static Set<String> combinationToCandidate(List<Integer> combination,
                                                          List<List<String>> desiredRuleKey,
                                                          String delimiter) {

            final int biggestRuleKeySize = desiredRuleKey.stream().mapToInt(List::size)
                    .boxed()
                    .max(Integer::compare)
                    .orElse(0);

            final List<List<String>> candidates = IntStream.range(0, desiredRuleKey.size())
                    .boxed()
                    .map(position -> candidatesForPosition(position, desiredRuleKey, biggestRuleKeySize))
                    .flatMap(Collection::stream)
                    .toList();

            for (final int positionToReplace : combination) {
                candidates.forEach(candidate -> candidate.set(positionToReplace, WILDCARD_CATCH_ALL));
            }

            return candidates.stream()
                    .map(candidate -> String.join(delimiter, candidate))
                    .collect(Collectors.toSet());
        }

        private static List<List<String>> candidatesForPosition(int multPosition,
                                                                List<List<String>> desiredRuleKey,
                                                                int biggestRuleKeySize) {
            return desiredRuleKey.get(multPosition).stream()
                    .flatMap(ruleKey -> IntStream.range(0, biggestRuleKeySize)
                            .mapToObj(i -> candidateForPosition(desiredRuleKey, ruleKey, multPosition, i)))
                    .toList();
        }

        private static List<String> candidateForPosition(List<List<String>> desiredRuleKey,
                                                         String currentRuleKey,
                                                         int currentPosition,
                                                         int position) {

            return IntStream.range(0, desiredRuleKey.size())
                    .mapToObj(index -> {
                        if (index == currentPosition) {
                            return currentRuleKey;
                        } else {
                            return getLastOrNext(desiredRuleKey.get(index), position);
                        }
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        private static String getLastOrNext(List<String> ruleKeys, int index) {
            if (ruleKeys.size() <= index) {
                return ruleKeys.get(ruleKeys.size() - 1);
            }

            return IterableUtils.get(ruleKeys, index);
        }
    }
}
