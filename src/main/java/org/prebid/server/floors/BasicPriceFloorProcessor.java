package org.prebid.server.floors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.algorithms.random.RandomPositiveWeightedEntrySupplier;
import org.prebid.server.util.algorithms.random.RandomWeightedEntrySupplier;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class BasicPriceFloorProcessor implements PriceFloorProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BasicPriceFloorProcessor.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final int SKIP_RATE_MIN = 0;
    private static final int SKIP_RATE_MAX = 100;
    private static final int MODEL_WEIGHT_MAX_VALUE = 100;
    private static final int MODEL_WEIGHT_MIN_VALUE = 1;

    private final PriceFloorFetcher floorFetcher;
    private final PriceFloorResolver floorResolver;
    private final JacksonMapper mapper;

    private final RandomWeightedEntrySupplier<PriceFloorModelGroup> modelPicker;

    public BasicPriceFloorProcessor(PriceFloorFetcher floorFetcher,
                                    PriceFloorResolver floorResolver,
                                    JacksonMapper mapper) {

        this.floorFetcher = Objects.requireNonNull(floorFetcher);
        this.floorResolver = Objects.requireNonNull(floorResolver);
        this.mapper = Objects.requireNonNull(mapper);

        modelPicker = new RandomPositiveWeightedEntrySupplier<>(BasicPriceFloorProcessor::resolveModelGroupWeight);
    }

    private static int resolveModelGroupWeight(PriceFloorModelGroup modelGroup) {
        return ObjectUtils.defaultIfNull(modelGroup.getModelWeight(), 1);
    }

    @Override
    public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final List<String> errors = auctionContext.getPrebidErrors();
        final List<String> warnings = auctionContext.getDebugWarnings();

        if (isPriceFloorsDisabled(account, bidRequest)) {
            return auctionContext.with(disableFloorsForRequest(bidRequest));
        }

        final PriceFloorRules floors = resolveFloors(account, bidRequest, errors);
        final BidRequest updatedBidRequest = updateBidRequestWithFloors(bidRequest, floors, errors, warnings);

        return auctionContext.with(updatedBidRequest);
    }

    private static boolean isPriceFloorsDisabled(Account account, BidRequest bidRequest) {
        return isPriceFloorsDisabledForAccount(account) || isPriceFloorsDisabledForRequest(bidRequest);
    }

    private static BidRequest disableFloorsForRequest(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final PriceFloorRules rules = ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);

        final PriceFloorRules updatedRules = (rules != null ? rules.toBuilder() : PriceFloorRules.builder())
                .enabled(false)
                .build();
        final ExtRequestPrebid updatedPrebid = (prebid != null ? prebid.toBuilder() : ExtRequestPrebid.builder())
                .floors(updatedRules)
                .build();

        return bidRequest.toBuilder()
                .ext(ExtRequest.of(updatedPrebid))
                .build();
    }

    private static boolean isPriceFloorsDisabledForAccount(Account account) {
        final AccountPriceFloorsConfig priceFloors = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);
        return BooleanUtils.isFalse(ObjectUtil.getIfNotNull(priceFloors, AccountPriceFloorsConfig::getEnabled));
    }

    private static boolean isPriceFloorsDisabledForRequest(BidRequest bidRequest) {
        final PriceFloorRules requestFloors = extractRequestFloors(bidRequest);
        return BooleanUtils.isFalse(ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getEnabled));
    }

    private static PriceFloorRules extractRequestFloors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private PriceFloorRules resolveFloors(Account account, BidRequest bidRequest, List<String> errors) {
        final PriceFloorRules requestFloors = extractRequestFloors(bidRequest);

        final FetchResult fetchResult = floorFetcher.fetch(account);
        final FetchStatus fetchStatus = ObjectUtil.getIfNotNull(fetchResult, FetchResult::getFetchStatus);

        if (shouldUseDynamicData(account) && fetchResult != null && fetchStatus == FetchStatus.success) {
            final PriceFloorRules mergedFloors = mergeFloors(requestFloors, fetchResult.getRulesData());
            return createFloorsFrom(mergedFloors, fetchStatus, PriceFloorLocation.fetch);
        }

        if (requestFloors != null) {
            try {
                PriceFloorRulesValidator.validateRules(requestFloors, Integer.MAX_VALUE);
                return createFloorsFrom(requestFloors, fetchStatus, PriceFloorLocation.request);
            } catch (PreBidException e) {
                errors.add("Failed to parse price floors from request, with a reason : %s ".formatted(e.getMessage()));
                conditionalLogger.error(
                        "Failed to parse price floors from request with id: '%s', with a reason : %s "
                                .formatted(bidRequest.getId(), e.getMessage()),
                        0.01d);
            }
        }

        return createFloorsFrom(null, fetchStatus, PriceFloorLocation.noData);
    }

    private static boolean shouldUseDynamicData(Account account) {
        final AccountAuctionConfig auctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);
        final AccountPriceFloorsConfig floorsConfig =
                ObjectUtil.getIfNotNull(auctionConfig, AccountAuctionConfig::getPriceFloors);

        return BooleanUtils.isNotFalse(
                ObjectUtil.getIfNotNull(floorsConfig, AccountPriceFloorsConfig::getUseDynamicData));
    }

    private PriceFloorRules mergeFloors(PriceFloorRules requestFloors,
                                        PriceFloorData providerRulesData) {

        final Price floorMinPrice = resolveFloorMinPrice(requestFloors);

        return (requestFloors != null ? requestFloors.toBuilder() : PriceFloorRules.builder())
                .floorMinCur(ObjectUtil.getIfNotNull(floorMinPrice, Price::getCurrency))
                .floorMin(ObjectUtil.getIfNotNull(floorMinPrice, Price::getValue))
                .data(providerRulesData)
                .build();
    }

    private Price resolveFloorMinPrice(PriceFloorRules requestFloors) {
        final String requestDataCurrency =
                ObjectUtil.getIfNotNull(
                        ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getData), PriceFloorData::getCurrency);
        final String requestFloorMinCur =
                ObjectUtils.firstNonNull(ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getFloorMinCur),
                        requestDataCurrency);
        final BigDecimal requestFloorMin = ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getFloorMin);

        if (StringUtils.isNotBlank(requestFloorMinCur) && BidderUtil.isValidPrice(requestFloorMin)) {
            return Price.of(requestFloorMinCur, requestFloorMin);
        }

        return Price.of(null, requestFloorMin);
    }

    private PriceFloorRules createFloorsFrom(PriceFloorRules floors,
                                             FetchStatus fetchStatus,
                                             PriceFloorLocation location) {

        final PriceFloorData floorData = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final PriceFloorData updatedFloorData = floorData != null ? updateFloorData(floorData) : null;

        return (floors != null ? floors.toBuilder() : PriceFloorRules.builder())
                .floorProvider(resolveFloorProvider(floors))
                .fetchStatus(fetchStatus)
                .location(location)
                .data(updatedFloorData)
                .build();
    }

    private PriceFloorData updateFloorData(PriceFloorData floorData) {
        final List<PriceFloorModelGroup> modelGroups = floorData.getModelGroups();

        final PriceFloorModelGroup modelGroup = CollectionUtils.isNotEmpty(modelGroups)
                ? selectFloorModelGroup(modelGroups)
                : null;

        return modelGroup != null
                ? floorData.toBuilder().modelGroups(Collections.singletonList(modelGroup)).build()
                : floorData;
    }

    private PriceFloorModelGroup selectFloorModelGroup(List<PriceFloorModelGroup> modelGroups) {
        return modelPicker.get(
                modelGroups.stream()
                        .filter(BasicPriceFloorProcessor::isValidModelGroup)
                        .toList());
    }

    private static boolean isValidModelGroup(PriceFloorModelGroup modelGroup) {
        final Integer skipRate = modelGroup.getSkipRate();
        if (skipRate != null && (skipRate < SKIP_RATE_MIN || skipRate > SKIP_RATE_MAX)) {
            return false;
        }

        final Integer modelWeight = modelGroup.getModelWeight();
        return modelWeight == null
                || (modelWeight >= MODEL_WEIGHT_MIN_VALUE && modelWeight <= MODEL_WEIGHT_MAX_VALUE);
    }

    private static String resolveFloorProvider(PriceFloorRules rules) {
        final PriceFloorData floorData = ObjectUtil.getIfNotNull(rules, PriceFloorRules::getData);
        final String dataLevelProvider = ObjectUtil.getIfNotNull(floorData, PriceFloorData::getFloorProvider);

        return StringUtils.isNotBlank(dataLevelProvider)
                ? dataLevelProvider
                : ObjectUtil.getIfNotNull(rules, PriceFloorRules::getFloorProvider);
    }

    private BidRequest updateBidRequestWithFloors(BidRequest bidRequest,
                                                  PriceFloorRules floors,
                                                  List<String> errors,
                                                  List<String> warnings) {

        final Integer requestSkipRate = extractSkipRate(floors);
        final boolean skipFloors = shouldSkipFloors(requestSkipRate);

        final List<Imp> imps = skipFloors
                ? bidRequest.getImp()
                : updateImpsWithFloors(floors, bidRequest, errors, warnings);
        final ExtRequest extRequest = updateExtRequestWithFloors(bidRequest, floors, requestSkipRate, skipFloors);

        return bidRequest.toBuilder()
                .imp(imps)
                .ext(extRequest)
                .build();
    }

    private static boolean shouldSkipFloors(Integer skipRate) {
        return skipRate != null && ThreadLocalRandom.current().nextInt(SKIP_RATE_MAX) < skipRate;
    }

    private static Integer extractSkipRate(PriceFloorRules floors) {
        final PriceFloorModelGroup modelGroup = extractFloorModelGroup(floors);
        final Integer modelGroupSkipRate = ObjectUtil.getIfNotNull(modelGroup, PriceFloorModelGroup::getSkipRate);
        if (isValidSkipRate(modelGroupSkipRate)) {
            return modelGroupSkipRate;
        }

        final PriceFloorData data = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final Integer dataSkipRate = ObjectUtil.getIfNotNull(data, PriceFloorData::getSkipRate);
        if (isValidSkipRate(dataSkipRate)) {
            return dataSkipRate;
        }

        final Integer rootSkipRate = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getSkipRate);
        if (isValidSkipRate(rootSkipRate)) {
            return rootSkipRate;
        }

        return null;
    }

    private static boolean isValidSkipRate(Integer value) {
        return value != null && value >= SKIP_RATE_MIN && value <= SKIP_RATE_MAX;
    }

    private List<Imp> updateImpsWithFloors(PriceFloorRules effectiveFloors,
                                           BidRequest bidRequest,
                                           List<String> errors,
                                           List<String> warnings) {

        final List<Imp> imps = bidRequest.getImp();

        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final PriceFloorRules floors =
                ObjectUtils.defaultIfNull(effectiveFloors,
                        ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors));
        final PriceFloorModelGroup modelGroup = extractFloorModelGroup(floors);
        if (modelGroup == null) {
            return imps;
        }

        return CollectionUtils.emptyIfNull(imps).stream()
                .map(imp -> updateImpWithFloors(imp, floors, bidRequest, errors, warnings))
                .toList();
    }

    private static PriceFloorModelGroup extractFloorModelGroup(PriceFloorRules floors) {
        final PriceFloorData data = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final List<PriceFloorModelGroup> modelGroups = ObjectUtil.getIfNotNull(data, PriceFloorData::getModelGroups);

        return CollectionUtils.isNotEmpty(modelGroups) ? modelGroups.get(0) : null;
    }

    private Imp updateImpWithFloors(Imp imp,
                                    PriceFloorRules floorRules,
                                    BidRequest bidRequest,
                                    List<String> errors,
                                    List<String> warnings) {

        final PriceFloorResult priceFloorResult;
        try {
            priceFloorResult = floorResolver.resolve(bidRequest, floorRules, imp, warnings);
        } catch (IllegalStateException e) {
            errors.add("Cannot resolve bid floor, error: " + e.getMessage());
            return imp;
        }

        if (priceFloorResult == null) {
            return imp;
        }

        return imp.toBuilder()
                .bidfloor(priceFloorResult.getFloorValue())
                .bidfloorcur(priceFloorResult.getCurrency())
                .ext(updateImpExtWithFloors(imp.getExt(), priceFloorResult))
                .build();
    }

    private ObjectNode updateImpExtWithFloors(ObjectNode ext, PriceFloorResult priceFloorResult) {
        final JsonNode extPrebid = ext.path("prebid");
        final ObjectNode extPrebidAsObject = extPrebid.isObject()
                ? (ObjectNode) extPrebid
                : mapper.mapper().createObjectNode();

        final JsonNode impFloorsNode = extPrebid.get("floors");
        final ExtImpPrebidFloors prebidFloors = ExtImpPrebidFloors.of(
                priceFloorResult.getFloorRule(),
                priceFloorResult.getFloorRuleValue(),
                priceFloorResult.getFloorValue(),
                resolveImpFloorMin(impFloorsNode),
                resolveImpFloorMinCur(impFloorsNode));
        final ObjectNode floorsNode = mapper.mapper().valueToTree(prebidFloors);

        return floorsNode.isEmpty() ? ext : ext.set("prebid", extPrebidAsObject.set("floors", floorsNode));
    }

    private static BigDecimal resolveImpFloorMin(JsonNode impFloorsNode) {
        final JsonNode impFloorMinNode = impFloorsNode != null ? impFloorsNode.get("floorMin") : null;

        return impFloorMinNode != null && impFloorMinNode.isNumber()
                ? impFloorMinNode.decimalValue() : null;
    }

    private static String resolveImpFloorMinCur(JsonNode impFloorsNode) {
        final JsonNode impFloorMinCurNode = impFloorsNode != null ? impFloorsNode.get("floorMinCur") : null;

        return impFloorMinCurNode != null && impFloorMinCurNode.isTextual()
                ? impFloorMinCurNode.asText() : null;
    }

    private static ExtRequest updateExtRequestWithFloors(BidRequest bidRequest,
                                                         PriceFloorRules floors,
                                                         Integer skipRate,
                                                         boolean skipFloors) {

        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);

        final ExtRequestPrebid updatedPrebid = (prebid != null ? prebid.toBuilder() : ExtRequestPrebid.builder())
                .floors(skipFloors ? skippedFloors(floors, skipRate) : enabledFloors(floors, skipRate))
                .build();

        return ExtRequest.of(updatedPrebid);
    }

    private static PriceFloorRules enabledFloors(PriceFloorRules floors, Integer skipRate) {
        return floors.toBuilder()
                .skipRate(skipRate)
                .enabled(true)
                .skipped(false)
                .build();
    }

    private static PriceFloorRules skippedFloors(PriceFloorRules floors, Integer skipRate) {
        return floors.toBuilder()
                .skipRate(skipRate)
                .enabled(true)
                .skipped(true)
                .build();
    }
}
