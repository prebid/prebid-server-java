package org.prebid.server.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.settings.ApplicationSettings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CategoryMapper {

    private static final TypeReference<Map<String, ExtImpDealTier>> EXT_IMP_DEAL_TIER_REFERENCE =
            new TypeReference<Map<String, ExtImpDealTier>>() {
            };

    private static final String FREEWHEEL = "freewheel";
    private static final String DFP = "dfp";
    private static final String CONTEXT = "context";
    private static final String PREBID = "prebid";

    private final ApplicationSettings applicationSettings;
    private final JacksonMapper jacksonMapper;

    public CategoryMapper(ApplicationSettings applicationSettings, JacksonMapper jacksonMapper) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    /**
     * Creates mapping between bid and categoryDuration.
     * Drops bids with invalid or missing category.
     * Removes bids with duplicated category key, leaving one with highest price.
     * Returns list of errors for each dropped bid.
     */
    public Future<CategoryMappingResult> createCategoryMapping(List<BidderResponse> bidderResponses,
                                                               BidRequest bidRequest,
                                                               ExtRequestTargeting targeting,
                                                               Timeout timeout) {
        if (targeting == null || targeting.getIncludebrandcategory() == null) {
            return Future.succeededFuture(CategoryMappingResult.of(Collections.emptyMap(), bidderResponses,
                    Collections.emptyList()));
        }
        final ExtIncludeBrandCategory includeBrandCategory = targeting.getIncludebrandcategory();
        final boolean withCategory = BooleanUtils.toBooleanDefaultIfNull(includeBrandCategory.getWithCategory(), false);
        // translate category definition looks strange, but it follows GO PBS logic
        final boolean translateCategories = !withCategory
                || BooleanUtils.toBooleanDefaultIfNull(includeBrandCategory.getTranslateCategories(), true);

        final String primaryAdServer;
        try {
            primaryAdServer = translateCategories
                    ? getPrimaryAdServer(includeBrandCategory.getPrimaryAdserver())
                    : null;
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }
        final String publisher = translateCategories ? includeBrandCategory.getPublisher() : null;
        final List<RejectedBid> rejectedBids = new ArrayList<>();

        return makeBidderToBidCategory(bidderResponses, primaryAdServer, publisher, rejectedBids, timeout).future()
                .map(bidIdToCategory -> resolveBidsCategoriesDurations(bidderResponses, bidIdToCategory, bidRequest,
                        targeting, withCategory, rejectedBids));
    }

    /**
     * Converts integer ad server to string representation.
     * 1 - freewheel
     * 2 - dfp
     * other - throws {@link InvalidRequestException}
     */
    private static String getPrimaryAdServer(Integer primaryAdServer) {
        if (primaryAdServer == null) {
            throw new InvalidRequestException("Primary ad server required but was not defined"
                    + " when translate category is enabled");
        }
        switch (primaryAdServer) {
            case 1:
                return FREEWHEEL;
            case 2:
                return DFP;
            default:
                throw new InvalidRequestException(String.format("Primary ad server `%s` is not recognized",
                        primaryAdServer));
        }
    }

    /**
     * Creates mapping between bidId and translated category for each bidder.
     * Returns map with nested map, where external Map is relation between bidder to internal map,
     * which represents relation between bidId and category.
     */
    private Promise<Map<String, Map<String, String>>> makeBidderToBidCategory(List<BidderResponse> bidderResponses,
                                                                              String primaryAdServer,
                                                                              String publisher,
                                                                              List<RejectedBid> rejectedBids,
                                                                              Timeout timeout) {
        final Promise<Map<String, Map<String, String>>> bidderToBidsCategoriesPromise = Promise.promise();
        final CompositeFuture compositeFuture = CompositeFuture.join(bidderResponses.stream()
                .flatMap(bidderResponse -> makeFetchCategoryFutures(bidderResponse, primaryAdServer, publisher,
                        timeout))
                .collect(Collectors.toList()));
        compositeFuture.setHandler(event -> collectCategoryFetchResults(compositeFuture, bidderToBidsCategoriesPromise,
                rejectedBids));
        return bidderToBidsCategoriesPromise;
    }

    /**
     * Creates stream of futures to query category for each bid in {@link BidderResponse}.
     */
    private Stream<Future<CategoryBidContext>> makeFetchCategoryFutures(BidderResponse bidderResponse,
                                                                        String primaryAdServer,
                                                                        String publisher,
                                                                        Timeout timeout) {
        final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
        final String bidder = bidderResponse.getBidder();
        return bidderBids.stream()
                .map(bidderBid -> resolveCategory(primaryAdServer, publisher, bidderBid.getBid(), bidder, timeout));
    }

    /**
     * Fetches category from external source or from bid.cat.
     */
    private Future<CategoryBidContext> resolveCategory(String primaryAdServer, String publisher, Bid bid,
                                                       String bidder, Timeout timeout) {
        final String bidId = bid.getId();
        final List<String> cat = ListUtils.emptyIfNull(bid.getCat());
        if (cat.size() > 1) {
            return Future.failedFuture(new RejectedBidException(bid.getId(), bidder, "Bid has more than one category"));
        }
        if (CollectionUtils.isEmpty(cat) || StringUtils.isBlank(cat.get(0))) {
            return Future.failedFuture(new RejectedBidException(bid.getId(), bidder, "Bid did not contain a category"));
        }
        final String firstCat = cat.get(0);
        return StringUtils.isNotBlank(primaryAdServer)
                ? fetchCategory(primaryAdServer, publisher, bid.getId(), bidder, firstCat, timeout)
                : Future.succeededFuture(CategoryBidContext.builder().bidId(bidId).bidder(bidder).category(firstCat)
                .build());
    }

    /**
     * Queries external source for bid's category.
     */
    private Future<CategoryBidContext> fetchCategory(String primaryAdServer, String publisher, String bidId,
                                                     String bidder, String cat, Timeout timeout) {
        return applicationSettings.getCategories(primaryAdServer, publisher, timeout)
                .map(fetchedCategories -> findAndValidateCategory(fetchedCategories, cat, bidId, bidder,
                        primaryAdServer, publisher))
                .recover(throwable -> wrapWithRejectedBidException(bidId, bidder, throwable))
                .map(fetchedCategory -> CategoryBidContext.builder().bidId(bidId).bidder(bidder)
                        .category(fetchedCategory).build());
    }

    /**
     * Throws {@link RejectedBidException} when fetched category is null or empty.
     */
    private static String findAndValidateCategory(Map<String, String> fetchedCategories, String cat,
                                                  String bidId, String bidder,
                                                  String primaryAdServer, String publisher) {
        if (MapUtils.isEmpty(fetchedCategories)) {
            throw new RejectedBidException(bidId, bidder,
                    String.format("Category mapping data for primary ad server: '%s', publisher: '%s' not found",
                            primaryAdServer, publisher));
        }

        final String categoryId = fetchedCategories.get(cat);
        if (StringUtils.isEmpty(categoryId)) {
            throw new RejectedBidException(bidId, bidder, String.format("Category mapping data for primary ad server:"
                            + " '%s', publisher: '%s' does not contain category for cat = '%s'",
                    primaryAdServer, publisher, cat));
        }
        return categoryId;
    }

    /**
     * Collects categories from all futures working on retrieving category from external sources.
     * Drops corresponding bid for failed attempt to request category.
     */
    private static void collectCategoryFetchResults(CompositeFuture compositeFuture,
                                                    Promise<Map<String, Map<String, String>>> resultTrackerPromise,
                                                    List<RejectedBid> rejectedBids) {
        final Map<String, Map<String, String>> bidderToBidCategories = new HashMap<>();
        for (int i = 0; i < compositeFuture.list().size(); i++) {
            final Object bidderToBidCategory = compositeFuture.resultAt(i);
            if (bidderToBidCategory != null) {
                addBidderBidCategory(bidderToBidCategories, bidderToBidCategory);
            } else {
                final RejectedBidException rejectedBidException = (RejectedBidException) compositeFuture.cause(i);
                rejectedBids.add(rejectedBidException.getBid());
            }
        }
        resultTrackerPromise.complete(bidderToBidCategories);
    }

    /**
     * Casts fetch query result to correct types and puts it to resulting map in corresponding to bidder and bidId
     * place.
     */
    private static void addBidderBidCategory(Map<String, Map<String, String>> bidderToBidCategories,
                                             Object bidderToBidCategoryObject) {
        final CategoryBidContext categoryBidContext = (CategoryBidContext) bidderToBidCategoryObject;
        final String bidId = categoryBidContext.getBidId();
        final String bidder = categoryBidContext.getBidder();
        final String category = categoryBidContext.getCategory();
        if (bidderToBidCategories.get(bidder) == null) {
            final Map<String, String> bidToCategory = new HashMap<>();
            bidToCategory.put(bidId, category);
            bidderToBidCategories.put(bidder, bidToCategory);
        } else {
            bidderToBidCategories.get(bidder).put(bidId, category);
        }
    }

    /**
     * Wraps throwable with {@link RejectedBidException} to contain information about bidId and bidder
     * caused this exception,
     */
    private Future<String> wrapWithRejectedBidException(String bidId, String bidder, Throwable throwable) {
        return Future.failedFuture(new RejectedBidException(bidId, bidder, throwable.getMessage()));
    }

    /**
     * Creates duration dropping bids with duplicated categories.
     */
    private CategoryMappingResult resolveBidsCategoriesDurations(List<BidderResponse> bidderResponses,
                                                                 Map<String, Map<String, String>> bidderToBidCategory,
                                                                 BidRequest bidRequest,
                                                                 ExtRequestTargeting targeting,
                                                                 boolean withCategory,
                                                                 List<RejectedBid> rejectedBids) {
        final PriceGranularity priceGranularity = resolvePriceGranularity(targeting);
        final List<Integer> durations = ListUtils.emptyIfNull(targeting.getDurationrangesec());
        Collections.sort(durations);
        final List<String> errors = new ArrayList<>();
        final Map<String, Map<String, DealTier>> impIdToBiddersDealTear = extractDealsSupported(bidRequest)
                ? extractDealTierPerImpAndBidder(bidRequest.getImp(), errors)
                : Collections.emptyMap();

        final Map<String, Set<CategoryBidContext>> uniqueCatKeysToCategoryBids = bidderResponses.stream()
                .flatMap(bidderResponse -> initiateCategoryBidsStream(bidderResponse, rejectedBids))
                .map(categoryBidContext -> toCategoryBid(categoryBidContext, durations, priceGranularity, withCategory,
                        impIdToBiddersDealTear, bidderToBidCategory, rejectedBids))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(CategoryBidContext::getCategoryUniqueKey,
                        Collectors.mapping(Function.identity(), Collectors.toSet())));

        rejectedBids.addAll(collectRejectedDuplicatedBids(uniqueCatKeysToCategoryBids));
        errors.addAll(rejectedBids.stream().map(RejectedBid::getErrorMessage).collect(Collectors.toList()));
        return CategoryMappingResult.of(
                makeBidderToBidCategoryDuration(uniqueCatKeysToCategoryBids, rejectedBids),
                removeRejectedBids(bidderResponses, rejectedBids),
                errors);
    }

    /**
     * Returns video price granularity if exists, otherwise common price granularity.
     */
    private PriceGranularity resolvePriceGranularity(ExtRequestTargeting targeting) {
        final PriceGranularity videoPriceGranularity = parseVideoMediaTypePriceGranularity(targeting);
        return videoPriceGranularity != null
                ? videoPriceGranularity
                : makePriceGranularity(targeting.getPricegranularity(), "pricegranularity");
    }

    /**
     * Parses video type price granularity
     */
    private PriceGranularity parseVideoMediaTypePriceGranularity(ExtRequestTargeting targeting) {
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = targeting.getMediatypepricegranularity();
        return extMediaTypePriceGranularity != null
                ? makePriceGranularity(extMediaTypePriceGranularity.getVideo(), "mediatypepricegranularity.video")
                : null;
    }

    /**
     * Converts {@link JsonNode} price granularity to {@link PriceGranularity}.
     */
    private PriceGranularity makePriceGranularity(JsonNode nodePriceGranularity, String path) {
        final ExtPriceGranularity extPriceGranularity = nodePriceGranularity != null && !nodePriceGranularity.isNull()
                ? parsePriceGranularity(nodePriceGranularity, path)
                : null;
        return extPriceGranularity != null ? PriceGranularity.createFromExtPriceGranularity(extPriceGranularity) : null;
    }

    /**
     * Determines if deals are supported for category durations.
     */
    private boolean extractDealsSupported(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null
                && BooleanUtils.toBooleanDefaultIfNull(extRequestPrebid.getSupportdeals(), false);
    }

    /**
     * Extracts {@link DealTier}s from {@link List<Imp>} per imp per bidder.
     */
    private Map<String, Map<String, DealTier>> extractDealTierPerImpAndBidder(List<Imp> imps, List<String> errors) {
        return imps.stream().collect(Collectors.toMap(Imp::getId, imp -> extractBidderToDealTiers(imp, errors)));
    }

    /**
     * Extracts {@link DealTier}ss from {@link Imp} per bidder.
     */
    private Map<String, DealTier> extractBidderToDealTiers(Imp imp, List<String> errors) {
        final Map<String, ExtImpDealTier> biddersToImpExtDealTiers =
                jacksonMapper.mapper().convertValue(imp.getExt(), EXT_IMP_DEAL_TIER_REFERENCE);

        return biddersToImpExtDealTiers
                .entrySet().stream()
                .filter(bidderToImpExtDealTier -> isValidBidder(bidderToImpExtDealTier.getKey()))
                .map(bidderToImpExtDealTier -> Tuple2.of(bidderToImpExtDealTier.getKey(),
                        normalizeExtDealTier(bidderToImpExtDealTier.getValue())))
                .filter(biddersToImpExtDealTier -> isValidExtDealTier(biddersToImpExtDealTier.getLeft(),
                        biddersToImpExtDealTier.getRight(), imp.getId(), errors))
                .collect(Collectors.toMap(Tuple2::getLeft, tuple2 -> tuple2.getRight().getDealTier()));
    }

    /**
     * Normalizes {@link ExtImpDealTier} fields to correct format.
     */
    private ExtImpDealTier normalizeExtDealTier(ExtImpDealTier extImpDealTier) {
        if (extImpDealTier == null) {
            return null;
        }

        final DealTier dealTier = extImpDealTier.getDealTier();
        if (dealTier == null) {
            return extImpDealTier;
        }

        final String prefix = dealTier.getPrefix();
        final String normalizedPrefix = prefix != null ? StringUtils.replaceAll(prefix, " ", "") : null;
        return ExtImpDealTier.of(DealTier.of(normalizedPrefix, dealTier.getMinDealTier()));
    }

    /**
     * Returns true when bidder parameter is valid bidder name.
     */
    private boolean isValidBidder(String bidder) {
        return ObjectUtils.notEqual(bidder, PREBID) && ObjectUtils.notEqual(bidder, CONTEXT);
    }

    /**
     * Returns true if {@link ExtImpDealTier} is not null and has valid {@link DealTier} fields.
     */
    private boolean isValidExtDealTier(String bidder, ExtImpDealTier extImpDealTier, String impId,
                                       List<String> errors) {
        if (extImpDealTier == null || extImpDealTier.getDealTier() == null) {
            errors.add(String.format("DealTier configuration not defined for bidder '%s', imp ID '%s'", bidder, impId));
            return false;
        }

        final DealTier dealTier = extImpDealTier.getDealTier();
        if (StringUtils.isBlank(dealTier.getPrefix())) {
            errors.add(String.format("DealTier configuration not valid for bidder '%s', imp ID '%s' with a reason:"
                    + " dealTier.prefix empty string or null", bidder, impId));
            return false;
        }
        final Integer minDealTier = dealTier.getMinDealTier();
        if (minDealTier == null || minDealTier <= 0) {
            errors.add(String.format("DealTier configuration not valid for bidder '%s', imp ID '%s' with a reason:"
                    + " dealTier.minDealTier should be larger than 0, but was %s", bidder, impId, minDealTier));
            return false;
        }

        return true;
    }

    /**
     * Parse {@link JsonNode} to {@link List} of {@link ExtPriceGranularity}.
     * <p>
     * Throws {@link PreBidException} in case of errors during decoding price granularity.
     */
    private ExtPriceGranularity parsePriceGranularity(JsonNode priceGranularity, String path) {
        try {
            return jacksonMapper.mapper().treeToValue(priceGranularity, ExtPriceGranularity.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.prebid.targeting.%s: %s",
                    path, e.getMessage()), e);
        }
    }

    /**
     * Creates {@link Stream} {@link CategoryBidContext} to fill it with
     * initial data for further enriching and processing.
     */
    private Stream<CategoryBidContext> initiateCategoryBidsStream(BidderResponse bidderResponse,
                                                                  List<RejectedBid> rejectedBids) {
        final String bidder = bidderResponse.getBidder();
        final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
        return bidderBids.stream()
                .filter(bidderBid -> isNotRejected(bidderBid.getBid().getId(), bidder, rejectedBids))
                .map(bidderBid -> CategoryBidContext.builder().bidderBid(bidderBid)
                        .bidId(bidderBid.getBid().getId()).bidder(bidder).build());
    }

    /**
     * Returns true if there is not bid with bidId and bidder in rejectedBids parameter.
     */
    private static boolean isNotRejected(String bidId, String bidder, List<RejectedBid> rejectedBids) {
        return rejectedBids.stream()
                .noneMatch(rejectedBid -> rejectedBid.getBidId().equals(bidId)
                        && rejectedBid.getBidder().equals(bidder));
    }

    /**
     * Resolves necessary information to resolve category duration and decide which bid should be dropped as duplicated
     * and creates {@link CategoryBidContext} which is holder for bid category related information.
     */
    private CategoryBidContext toCategoryBid(CategoryBidContext categoryBidContext,
                                             List<Integer> durations,
                                             PriceGranularity priceGranularity,
                                             boolean withCategory,
                                             Map<String, Map<String, DealTier>> impToBiddersDealTier,
                                             Map<String, Map<String, String>> bidderToBidCategory,
                                             List<RejectedBid> rejectedBids) {
        final BidderBid bidderBid = categoryBidContext.getBidderBid();
        final Bid bid = bidderBid.getBid();
        final int duration;
        final String bidder = categoryBidContext.getBidder();
        try {
            duration = resolveDuration(durations, bidderBid, bidder);
        } catch (RejectedBidException e) {
            rejectedBids.add(e.getBid());
            return null;
        }
        final String bidId = bid.getId();
        final BigDecimal price = CpmRange.fromCpmAsNumber(bid.getPrice(), priceGranularity);
        final String rowPrice = CpmRange.format(price, priceGranularity.getPrecision());
        final String category = bidderToBidCategory.get(bidder).get(bidId);
        final String categoryUniqueKey = makeCategoryUniqueKey(rowPrice, duration, category,
                withCategory);
        final Map<String, DealTier> impsDealTiers = impToBiddersDealTier.get(bid.getImpid());
        final DealTier dealTier = impsDealTiers != null ? impsDealTiers.get(bidder) : null;
        final int dealPriority = bidderBid.getDealPriority() != null ? bidderBid.getDealPriority() : 0;
        final String categoryDuration = makeCategoryDuration(rowPrice, category, duration, dealPriority,
                dealTier, withCategory);

        return CategoryBidContext.builder()
                .bidId(bidId)
                .bidder(bidder)
                .categoryDuration(categoryDuration)
                .categoryUniqueKey(categoryUniqueKey)
                .price(price)
                .build();
    }

    /**
     * Resolves video duration for bid.
     */
    private Integer resolveDuration(List<Integer> durations, BidderBid bidderBid, String bidder) {
        final ExtBidPrebidVideo extBidPrebidVideo = bidderBid.getVideoInfo();
        final String bidId = bidderBid.getBid().getId();
        final Integer extPrebidVideoDuration = extBidPrebidVideo != null
                ? extBidPrebidVideo.getDuration()
                : null;
        final int duration = extPrebidVideoDuration != null ? extPrebidVideoDuration : 0;
        if (CollectionUtils.isEmpty(durations)) {
            return duration;
        }

        final int maxDuration = durations.get(durations.size() - 1);
        if (duration > maxDuration) {
            throw new RejectedBidException(bidId, bidder, String.format("Bid duration '%s' exceeds maximum '%s'",
                    duration, maxDuration));
        }

        return durations.stream()
                .filter(targetingDuration -> duration <= targetingDuration)
                .findFirst()
                .orElseThrow(() ->
                        new RejectedBidException(bidId, bidder, "Duration is not in targeting range"));
    }

    /**
     * Creates category key which used for finding duplicated bids.
     */
    private String makeCategoryUniqueKey(String price, int duration, String category, boolean withCategory) {
        return withCategory
                ? category
                : String.format("%s_%ds", price, duration);
    }

    /**
     * Creates category duration.
     */
    private String makeCategoryDuration(String price, String category, int duration, int bidPriority, DealTier dealTier,
                                        boolean withCategory) {
        final String categoryPrefix = dealTier != null && bidPriority >= dealTier.getMinDealTier()
                ? String.format("%s%d", dealTier.getPrefix(), dealTier.getMinDealTier())
                : price;
        return withCategory
                ? String.format("%s_%s_%ds", categoryPrefix, category, duration)
                : String.format("%s_%ds", categoryPrefix, duration);
    }

    /**
     * Collects all bids defined as duplicated to collection and creates {@link RejectedBid} with reason.
     */
    private Set<RejectedBid> collectRejectedDuplicatedBids(
            Map<String, Set<CategoryBidContext>> categoryToDuplicatedCategoryBids) {
        return categoryToDuplicatedCategoryBids.values().stream()
                .filter(categoryBids -> categoryBids.size() > 1)
                .flatMap(CategoryMapper::getDuplicatedForCategory)
                .map(categoryBidContext -> RejectedBid.of(categoryBidContext.getBidId(), categoryBidContext.getBidder(),
                        "Bid was deduplicated"))
                .collect(Collectors.toSet());
    }

    /**
     * Creates mapping between bidder and its map of bidId to category duration.
     */
    private Map<String, Map<String, String>> makeBidderToBidCategoryDuration(
            Map<String, Set<CategoryBidContext>> categoryToBidsWithBidder,
            List<RejectedBid> rejectedBids) {
        return categoryToBidsWithBidder.values().stream()
                .flatMap(Collection::stream)
                .filter(categoryBidContext -> isNotRejected(categoryBidContext.getBidId(),
                        categoryBidContext.getBidder(), rejectedBids))
                .collect(Collectors.groupingBy(CategoryBidContext::getBidder,
                        Collectors.toMap(CategoryBidContext::getBidId, CategoryBidContext::getCategoryDuration)));
    }

    /**
     * Removes rejected bids from  {@link List<BidderResponse>}.
     */
    private List<BidderResponse> removeRejectedBids(List<BidderResponse> bidderResponses,
                                                    List<RejectedBid> rejectedBids) {
        final Map<String, List<String>> rejectedBidderBid = rejectedBids
                .stream()
                .collect(Collectors.groupingBy(RejectedBid::getBidder,
                        Collectors.mapping(RejectedBid::getBidId, Collectors.toList())));
        return bidderResponses.stream()
                .map(bidderResponse -> rejectedBidderBid.containsKey(bidderResponse.getBidder())
                        ? removeRejectedBids(bidderResponse, rejectedBidderBid)
                        : bidderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Remove rejected bids from {@link BidderResponse}.
     */
    private BidderResponse removeRejectedBids(BidderResponse bidderResponse,
                                              Map<String, List<String>> rejectedBidderBid) {
        final String bidder = bidderResponse.getBidder();
        final List<String> rejectedIds = rejectedBidderBid.get(bidder);
        final List<BidderBid> survivedBidderBids = bidderResponse.getSeatBid().getBids().stream()
                .filter(bidderBid -> !rejectedIds.contains(bidderBid.getBid().getId()))
                .collect(Collectors.toList());
        final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();
        return BidderResponse.of(bidder,
                BidderSeatBid.of(survivedBidderBids, bidderSeatBid.getHttpCalls(), bidderSeatBid.getErrors()),
                bidderResponse.getResponseTime());
    }

    /**
     * Creates {@link Stream<CategoryBidContext>} of duplicated bids, adding bids with price lower than bid with max
     * price.
     */
    private static Stream<CategoryBidContext> getDuplicatedForCategory(Set<CategoryBidContext> categoryBidContexts) {
        final CategoryBidContext highestPriceBid = categoryBidContexts.stream()
                .reduce((bid1, bid2) -> bid1.getPrice().compareTo(bid2.getPrice()) > 0 ? bid1 : bid2)
                .orElseThrow(() -> new PreBidException("Can't find bid with highest price."));
        return categoryBidContexts.stream().filter(categoryBidContext -> ObjectUtils.notEqual(highestPriceBid,
                categoryBidContext));

    }

    /**
     * Creates message for rejected bid containing its id, bidder and reason.
     */
    private static String createRejectionMessage(String bidId, String bidder, String reason) {
        return String.format("Bid rejected [bidder: %s, bid ID: %s] with a reason: %s", bidder, bidId, reason);
    }

    /**
     * Exception for rejected bids case with rejected bid itself.
     */
    private static class RejectedBidException extends RuntimeException {
        private final RejectedBid rejectedBid;

        RejectedBidException(String bidId, String bidder, String error) {
            super(error);
            this.rejectedBid = RejectedBid.of(bidId, bidder, error);
        }

        public RejectedBid getBid() {
            return rejectedBid;
        }
    }

    /**
     * Holder of information about rejected bid.
     */
    @Value
    @AllArgsConstructor
    private static class RejectedBid {
        String bidId;
        String bidder;
        String errorMessage;

        private static RejectedBid of(String bidId, String bidder, String errorMessage) {
            return new RejectedBid(bidId, bidder, createRejectionMessage(bidId, bidder, errorMessage));
        }
    }

    /**
     * Holder for bid's category information.
     */
    @Value
    @Builder(toBuilder = true)
    private static class CategoryBidContext {
        String bidder;
        String bidId;
        BidderBid bidderBid;
        String category;
        String categoryDuration;
        String categoryUniqueKey;
        BigDecimal price;
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    private static class ExtImpDealTier {
        @JsonProperty("dealTier")
        DealTier dealTier;
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    private static class DealTier {
        String prefix;

        @JsonProperty("minDealTier")
        Integer minDealTier;
    }
}

