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
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.util.ObjectUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BasicPriceFloorProcessor implements PriceFloorProcessor {

    private static final int SKIP_RATE_MIN = 0;
    private static final int SKIP_RATE_MAX = 100;
    private static final int MODEL_WEIGHT_MAX_VALUE = 1_000_000;
    private static final int MODEL_WEIGHT_MIN_VALUE = 0;

    private final PriceFloorFetcher floorFetcher;
    private final PriceFloorResolver floorResolver;
    private final JacksonMapper mapper;

    public BasicPriceFloorProcessor(PriceFloorFetcher floorFetcher,
                                    PriceFloorResolver floorResolver,
                                    JacksonMapper mapper) {

        this.floorFetcher = Objects.requireNonNull(floorFetcher);
        this.floorResolver = Objects.requireNonNull(floorResolver);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final List<String> errors = auctionContext.getPrebidErrors();

        if (isPriceFloorsDisabled(account, bidRequest)) {
            return auctionContext;
        }

        final PriceFloorRules floors = resolveFloors(account, bidRequest);
        final BidRequest updatedBidRequest = updateBidRequestWithFloors(bidRequest, floors, errors);

        return auctionContext.with(updatedBidRequest);
    }

    private boolean isPriceFloorsDisabled(Account account, BidRequest bidRequest) {
        return isPriceFloorsDisabledForAccount(account) || isPriceFloorsDisabledForRequest(bidRequest);
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

    private PriceFloorRules resolveFloors(Account account, BidRequest bidRequest) {
        final PriceFloorRules requestFloors = extractRequestFloors(bidRequest);

        final FetchResult fetchResult = floorFetcher.fetch(account);
        if (fetchResult != null) {
            final PriceFloorRules mergedFloors = mergeFloors(requestFloors, fetchResult.getRules());
            return createFloorsFrom(mergedFloors, fetchResult.getFetchStatus(), PriceFloorLocation.provider);
        }

        if (requestFloors != null) {
            return createFloorsFrom(requestFloors, null, PriceFloorLocation.request);
        }

        return createFloorsFrom(null, null, PriceFloorLocation.none);
    }

    private static PriceFloorRules mergeFloors(PriceFloorRules requestFloors,
                                               PriceFloorRules providerFloors) {

        final Boolean floorsEnabled = ObjectUtil.getIfNotNull(requestFloors, PriceFloorRules::getEnabled);

        if (floorsEnabled != null) {
            return (providerFloors != null ? providerFloors.toBuilder() : PriceFloorRules.builder())
                    .enabled(requestFloors.getEnabled())
                    .build();
        }

        return providerFloors;
    }

    private static PriceFloorRules createFloorsFrom(PriceFloorRules floors,
                                                    FetchStatus fetchStatus,
                                                    PriceFloorLocation location) {

        final PriceFloorData floorData = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final PriceFloorData updatedFloorData = floorData != null ? updateFloorData(floorData) : null;

        return (floors != null ? floors.toBuilder() : PriceFloorRules.builder())
                .fetchStatus(fetchStatus)
                .location(location)
                .data(updatedFloorData)
                .build();
    }

    private static PriceFloorData updateFloorData(PriceFloorData floorData) {
        final List<PriceFloorModelGroup> modelGroups = floorData.getModelGroups();

        final PriceFloorModelGroup modelGroup = CollectionUtils.isNotEmpty(modelGroups)
                ? selectFloorModelGroup(modelGroups)
                : null;

        return modelGroup != null
                ? floorData.toBuilder().modelGroups(Collections.singletonList(modelGroup)).build()
                : floorData;
    }

    private static PriceFloorModelGroup selectFloorModelGroup(List<PriceFloorModelGroup> modelGroups) {
        final int overallModelWeight = modelGroups.stream()
                .filter(modelGroup -> isValidModelWeight(modelGroup.getModelWeight()))
                .mapToInt(BasicPriceFloorProcessor::resolveModelGroupWeight)
                .sum();

        Collections.shuffle(modelGroups);

        final List<PriceFloorModelGroup> groupsByWeight = modelGroups.stream()
                .filter(modelGroup -> isValidModelWeight(modelGroup.getModelWeight()))
                .sorted(Comparator.comparing(PriceFloorModelGroup::getModelWeight))
                .collect(Collectors.toList());

        int winWeight = ThreadLocalRandom.current().nextInt(overallModelWeight);
        for (PriceFloorModelGroup modelGroup : groupsByWeight) {
            winWeight -= resolveModelGroupWeight(modelGroup);

            if (winWeight <= 0) {
                return modelGroup;
            }
        }

        return groupsByWeight.get(groupsByWeight.size() - 1);
    }

    private static boolean isValidModelWeight(Integer modelWeight) {
        return modelWeight == null || (modelWeight > MODEL_WEIGHT_MIN_VALUE && modelWeight < MODEL_WEIGHT_MAX_VALUE);
    }

    private static int resolveModelGroupWeight(PriceFloorModelGroup modelGroup) {
        return ObjectUtils.defaultIfNull(modelGroup.getModelWeight(), 1);
    }

    private BidRequest updateBidRequestWithFloors(BidRequest bidRequest, PriceFloorRules floors, List<String> errors) {
        final boolean skipFloors = shouldSkipFloors(floors);

        final List<Imp> imps = skipFloors ? bidRequest.getImp() : updateImpsWithFloors(floors, bidRequest, errors);
        final ExtRequest extRequest = updateExtRequestWithFloors(bidRequest, floors, skipFloors);

        return bidRequest.toBuilder()
                .imp(imps)
                .ext(extRequest)
                .build();
    }

    private static boolean shouldSkipFloors(PriceFloorRules floors) {
        final Integer skipRate = extractSkipRate(floors);

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

    private List<Imp> updateImpsWithFloors(PriceFloorRules accountFloors, BidRequest bidRequest, List<String> errors) {
        final List<Imp> imps = bidRequest.getImp();

        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final PriceFloorRules floors =
                ObjectUtils.defaultIfNull(accountFloors, ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors));
        final PriceFloorModelGroup modelGroup = extractFloorModelGroup(floors);
        if (modelGroup == null) {
            return imps;
        }

        return CollectionUtils.emptyIfNull(imps).stream()
                .map(imp -> updateImpWithFloors(imp, modelGroup, bidRequest, errors))
                .collect(Collectors.toList());
    }

    private static PriceFloorModelGroup extractFloorModelGroup(PriceFloorRules floors) {
        final PriceFloorData data = ObjectUtil.getIfNotNull(floors, PriceFloorRules::getData);
        final List<PriceFloorModelGroup> modelGroups = ObjectUtil.getIfNotNull(data, PriceFloorData::getModelGroups);

        return CollectionUtils.isNotEmpty(modelGroups) ? modelGroups.get(0) : null;
    }

    private Imp updateImpWithFloors(Imp imp,
                                    PriceFloorModelGroup modelGroup,
                                    BidRequest bidRequest,
                                    List<String> errors) {

        final String currency = resolveFloorCurrency(imp, bidRequest);

        final PriceFloorResult priceFloorResult;
        try {
            priceFloorResult = floorResolver.resolve(bidRequest, modelGroup, imp, currency);
        } catch (IllegalStateException e) {
            errors.add(String.format("Cannot resolve bid floor, error: %s", e.getMessage()));
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

    private static String resolveFloorCurrency(Imp imp, BidRequest bidRequest) {
        final String impCurrency = StringUtils.stripToNull(imp.getBidfloorcur());
        if (impCurrency != null) {
            return impCurrency;
        }

        final List<String> requestCurrencies = bidRequest.getCur();
        return CollectionUtils.isNotEmpty(requestCurrencies) ? requestCurrencies.get(0) : null;
    }

    private ObjectNode updateImpExtWithFloors(ObjectNode ext, PriceFloorResult priceFloorResult) {
        final JsonNode extPrebid = ext.path("prebid");
        final ObjectNode extPrebidAsObject = extPrebid.isObject()
                ? (ObjectNode) extPrebid
                : mapper.mapper().createObjectNode();

        final ExtImpPrebidFloors prebidFloors = ExtImpPrebidFloors.of(priceFloorResult.getFloorRule(),
                priceFloorResult.getFloorRuleValue(), priceFloorResult.getFloorValue());
        final ObjectNode floorsNode = mapper.mapper().valueToTree(prebidFloors);

        return floorsNode.isEmpty() ? ext : ext.set("prebid", extPrebidAsObject.set("floors", floorsNode));
    }

    private static ExtRequest updateExtRequestWithFloors(BidRequest bidRequest,
                                                         PriceFloorRules floors,
                                                         boolean skipFloors) {

        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);

        final ExtRequestPrebid updatedPrebid = (prebid != null ? prebid.toBuilder() : ExtRequestPrebid.builder())
                .floors(skipFloors ? skippedFloors(floors) : floors)
                .build();

        return ExtRequest.of(updatedPrebid);
    }

    private static PriceFloorRules skippedFloors(PriceFloorRules floors) {
        return floors.toBuilder()
                .skipped(true)
                .build();
    }
}
