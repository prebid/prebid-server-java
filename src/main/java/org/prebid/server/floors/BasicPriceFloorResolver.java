package org.prebid.server.floors;

import com.fasterxml.jackson.core.JsonPointer;
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
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
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

    private final CurrencyConversionService currencyConversionService;
    private final CountryCodeMapper countryCodeMapper;
    private final Metrics metrics;

    public BasicPriceFloorResolver(CurrencyConversionService currencyConversionService,
                                   CountryCodeMapper countryCodeMapper,
                                   Metrics metrics) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public PriceFloorResult resolve(BidRequest bidRequest,
                                    PriceFloorRules floorRules,
                                    Imp imp,
                                    ImpMediaType mediaType,
                                    Format format,
                                    List<String> warnings) {

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
            return resolveResult(floor, rule, floorForRule, bidRequest, floorCurrency);
        } catch (PreBidException e) {
            final String logMessage =
                    String.format("Error occurred while resolving floor for imp: %s, cause: %s",
                            imp.getId(), e.getMessage());
            if (warnings != null) {
                warnings.add(logMessage);
            }
            logger.debug(logMessage);
            conditionalLogger.error(logMessage, 0.01d);
            metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);
        }

        return null;
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
                .collect(Collectors.toList());
    }

    private List<String> toFieldValues(PriceFloorField field,
                                       BidRequest bidRequest,
                                       Imp imp,
                                       ImpMediaType mediaType,
                                       Format format) {

        final List<ImpMediaType> resolvedMediaTypes = mediaType != null
                ? Collections.singletonList(mediaType)
                : mediaTypesFromImp(imp);

        switch (field) {
            case siteDomain:
                return siteDomainFromRequest(bidRequest);
            case pubDomain:
                return pubDomainFromRequest(bidRequest);
            case domain:
                return domainFromRequest(bidRequest);
            case bundle:
                return bundleFromRequest(bidRequest);
            case channel:
                return channelFromRequest(bidRequest);
            case mediaType:
                return mediaTypeToRuleKey(resolvedMediaTypes);
            case size:
                return sizeFromFormat(ObjectUtils.defaultIfNull(format, resolveFormatFromImp(imp, resolvedMediaTypes)));
            case gptSlot:
                return gptAdSlotFromImp(imp);
            case pbAdSlot:
                return pbAdSlotFromImp(imp);
            case country:
                return countryFromRequest(bidRequest);
            case deviceType:
                return resolveDeviceTypeFromRequest(bidRequest);
            default:
                throw new IllegalStateException("Unknown field type");
        }
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
                ? String.format("%dx%d", size.getW(), size.getH())
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

    private static List<String> pbAdSlotFromImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();

        if (impExt == null) {
            return null;
        }

        final JsonNode adSlotNode = impExt.at(PB_ADSLOT_POINTER);
        final String adSlot = !adSlotNode.isMissingNode() ? adSlotNode.asText() : null;

        return Collections.singletonList(adSlot);
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
                .collect(Collectors.toList());

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
                                           BidRequest bidRequest,
                                           String rulesCurrency) {

        if (floor == null) {
            return null;
        }

        final PriceFloorRules floorRules = extractRules(bidRequest);
        final BigDecimal floorMin = ObjectUtil.getIfNotNull(floorRules, PriceFloorRules::getFloorMin);
        final String floorMinCur = ObjectUtil.getIfNotNull(floorRules, PriceFloorRules::getFloorMinCur);

        final String effectiveRulesCurrency = ObjectUtils.defaultIfNull(rulesCurrency, DEFAULT_RULES_CURRENCY);
        final String effectiveFloorMinCurrency =
                ObjectUtils.firstNonNull(floorMinCur, rulesCurrency, DEFAULT_RULES_CURRENCY);

        final BigDecimal convertedFloorMinValue = !StringUtils.equals(effectiveRulesCurrency, effectiveFloorMinCurrency)
                ? convertCurrency(floorMin, bidRequest, effectiveFloorMinCurrency, effectiveRulesCurrency)
                : null;

        final Price effectiveFloor = Price.of(effectiveRulesCurrency, floor);
        final Price effectiveFloorMin = Price.of(effectiveFloorMinCurrency, floorMin);
        final Price convertedFloorMin = convertedFloorMinValue != null
                ? Price.of(effectiveRulesCurrency, convertedFloorMinValue)
                : Price.of(effectiveFloorMinCurrency, floorMin);

        final BigDecimal floorValue = effectiveFloor.getValue();
        final String floorCurrency = effectiveFloor.getCurrency();

        final BigDecimal floorMinValue = convertedFloorMin.getValue();
        final String floorMinCurrency = convertedFloorMin.getCurrency();

        final Price resolvedPrice;
        if (StringUtils.equals(floorCurrency, floorMinCurrency) && floorValue != null && floorMinValue != null) {
            if (floorValue.compareTo(floorMinValue) > 0) {
                resolvedPrice = roundPrice(effectiveFloor);
            } else {
                resolvedPrice = roundPrice(convertedFloorMin);
            }
        } else {
            resolvedPrice = roundPrice(ObjectUtils.defaultIfNull(effectiveFloor, effectiveFloorMin));
        }

        return PriceFloorResult.of(
                rule,
                floorForRule,
                ObjectUtil.getIfNotNull(resolvedPrice, Price::getValue),
                ObjectUtil.getIfNotNull(resolvedPrice, Price::getCurrency));
    }

    private static PriceFloorRules extractRules(BidRequest bidRequest) {
        final ExtRequest extRequest = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getExt);
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);

        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getFloors);
    }

    private BigDecimal convertCurrency(BigDecimal floor,
                                       BidRequest bidRequest,
                                       String currentCurrency,
                                       String desiredCurrency) {

        return currencyConversionService.convertCurrency(floor, bidRequest, currentCurrency, desiredCurrency);
    }

    private static Price roundPrice(Price price) {
        return price != null ? Price.of(price.getCurrency(), BidderUtil.roundFloor(price.getValue())) : null;
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
                    .collect(Collectors.toList());
        }

        private Iterator<String> createIterator(int wildcardNum, List<List<String>> desiredRuleKey, String delimiter) {
            final int ruleSegmentsNum = desiredRuleKey.size();

            return asStream(CombinatoricsUtils.combinationsIterator(ruleSegmentsNum, wildcardNum))
                    .map(combination -> IntStream.of(combination).boxed().collect(Collectors.toList()))
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

            int biggestRuleKeySize = desiredRuleKey.stream().mapToInt(List::size)
                    .boxed()
                    .max(Integer::compare)
                    .orElse(0);

            final List<List<String>> candidates = IntStream.range(0, desiredRuleKey.size())
                    .boxed()
                    .map(position -> candidatesForPosition(position, desiredRuleKey, biggestRuleKeySize))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

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
                    .collect(Collectors.toList());
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
                    .collect(Collectors.toList());
        }

        private static String getLastOrNext(List<String> ruleKeys, int index) {
            if (ruleKeys.size() <= index) {
                return ruleKeys.get(ruleKeys.size() - 1);
            }

            return IterableUtils.get(ruleKeys, index);
        }
    }
}
