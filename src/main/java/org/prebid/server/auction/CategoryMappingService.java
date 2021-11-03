package org.prebid.server.auction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtDealTier;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CategoryMappingService {

    private static final TypeReference<Map<String, DealTierContainer>> EXT_IMP_DEAL_TIER_REFERENCE =
            new TypeReference<>() {
            };

    private static final String FREEWHEEL_AD_SERVER = "freewheel";
    private static final String DFP_AD_SERVER = "dfp";

    private final ApplicationSettings applicationSettings;
    private final JacksonMapper jacksonMapper;

    public CategoryMappingService(ApplicationSettings applicationSettings, JacksonMapper jacksonMapper) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    /**
     * Creates mapping between bid and categoryDuration.
     * Drops the bids with invalid or missing category.
     * Removes the bids with duplicated category key, leaving one with the highest price.
     * Returns list of errors for each dropped bid.
     */
    public Future<CategoryMappingResult> createCategoryMapping(List<BidderResponse> bidderResponses,
                                                               BidRequest bidRequest,
                                                               ExtRequestTargeting targeting,
                                                               Timeout timeout) {

        final ExtIncludeBrandCategory includeBrandCategory = ObjectUtil.getIfNotNull(
                targeting, ExtRequestTargeting::getIncludebrandcategory);

        if (includeBrandCategory == null) {
            return Future.succeededFuture(
                    CategoryMappingResult.of(
                            Collections.emptyMap(),
                            Collections.emptyMap(),
                            bidderResponses,
                            Collections.emptyList()));
        }

        final boolean withCategory = BooleanUtils.toBooleanDefaultIfNull(
                includeBrandCategory.getWithCategory(), false);

        final boolean translateCategories = BooleanUtils.toBooleanDefaultIfNull(
                includeBrandCategory.getTranslateCategories(), true);

        final String primaryAdServer = withCategory && translateCategories
                ? getPrimaryAdServer(includeBrandCategory.getPrimaryAdserver())
                : null;
        final String publisher = withCategory && translateCategories
                ? includeBrandCategory.getPublisher()
                : null;

        final List<RejectedBid> rejectedBids = new ArrayList<>();

        return makeBidderToBidCategory(
                bidderResponses, withCategory, translateCategories, primaryAdServer, publisher, rejectedBids, timeout)
                .map(categoryBidContexts -> resolveBidsCategoriesDurations(
                        bidderResponses, categoryBidContexts, bidRequest, targeting, withCategory, rejectedBids));
    }

    /**
     * Converts integer ad server to string representation or throws exception if unknown.
     */
    private static String getPrimaryAdServer(Integer primaryAdServer) {
        if (primaryAdServer == null) {
            throw new InvalidRequestException(
                    "Primary ad server required but was not defined when translate category is enabled");
        }
        switch (primaryAdServer) {
            case 1:
                return FREEWHEEL_AD_SERVER;
            case 2:
                return DFP_AD_SERVER;
            default:
                throw new InvalidRequestException(
                        String.format("Primary ad server `%s` is not recognized", primaryAdServer));
        }
    }

    /**
     * Returns the list of contexts which represents relation between bid and category information.
     */
    private Future<List<CategoryBidContext>> makeBidderToBidCategory(List<BidderResponse> bidderResponses,
                                                                     boolean withCategory,
                                                                     boolean translateCategories,
                                                                     String primaryAdServer,
                                                                     String publisher,
                                                                     List<RejectedBid> rejectedBids,
                                                                     Timeout timeout) {

        final Promise<List<CategoryBidContext>> categoryBidContextsPromise = Promise.promise();

        final CompositeFuture compositeFuture = CompositeFuture.join(bidderResponses.stream()
                .flatMap(bidderResponse -> makeFetchCategoryFutures(
                        bidderResponse, primaryAdServer, publisher, timeout, withCategory, translateCategories))
                .collect(Collectors.toList()));

        compositeFuture.setHandler(ignored ->
                collectCategoryFetchResults(compositeFuture, categoryBidContextsPromise, rejectedBids));

        return categoryBidContextsPromise.future();
    }

    /**
     * Creates stream of futures to query category for each bid in {@link BidderResponse}.
     */
    private Stream<Future<CategoryBidContext>> makeFetchCategoryFutures(BidderResponse bidderResponse,
                                                                        String primaryAdServer,
                                                                        String publisher,
                                                                        Timeout timeout,
                                                                        boolean withCategory,
                                                                        boolean translateCategories) {

        final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
        final String bidder = bidderResponse.getBidder();
        return bidderBids.stream()
                .map(bidderBid -> resolveCategory(
                        primaryAdServer, publisher, bidderBid, bidder, timeout, withCategory, translateCategories));
    }

    /**
     * Fetches category from external source or from bid.cat.
     */
    private Future<CategoryBidContext> resolveCategory(String primaryAdServer,
                                                       String publisher,
                                                       BidderBid bidderBid,
                                                       String bidder,
                                                       Timeout timeout,
                                                       boolean withCategory,
                                                       boolean translateCategories) {

        final Bid bid = bidderBid.getBid();

        final String videoPrimaryCategory = getVideoBidPrimaryCategory(bidderBid);
        if (StringUtils.isNotBlank(videoPrimaryCategory)) {
            return Future.succeededFuture(CategoryBidContext.of(bidderBid, bidder, videoPrimaryCategory));
        }

        if (!withCategory) {
            return Future.succeededFuture(CategoryBidContext.of(bidderBid, bidder, null));
        }

        final List<String> iabCategories = ListUtils.emptyIfNull(bid.getCat());
        if (iabCategories.size() > 1) {
            return Future.failedFuture(
                    new RejectedBidException(bid.getId(), bidder, "Bid has more than one category"));
        }
        final String category = CollectionUtils.isNotEmpty(iabCategories) ? iabCategories.get(0) : null;
        if (StringUtils.isBlank(category)) {
            return Future.failedFuture(
                    new RejectedBidException(bid.getId(), bidder, "Bid did not contain a category"));
        }

        return translateCategories
                ? fetchCategory(bidderBid, bidder, primaryAdServer, publisher, category, timeout)
                : Future.succeededFuture(CategoryBidContext.of(bidderBid, bidder, category));
    }

    private String getVideoBidPrimaryCategory(BidderBid bidderBid) {
        // TODO: GET RID OF THIS ALONG WITH videoInfo FIELD IN BidderBid!!!!
        final String wrapperVideoPrimaryCategory = ObjectUtil.getIfNotNull(
                bidderBid.getVideoInfo(), ExtBidPrebidVideo::getPrimaryCategory);
        if (wrapperVideoPrimaryCategory != null) {
            return wrapperVideoPrimaryCategory;
        }

        final ObjectNode bidExt = bidderBid.getBid().getExt();
        final ExtBidPrebid extPrebid = bidExt != null ? toExtBidPrebid(bidExt) : null;
        final ExtBidPrebidVideo extVideo = extPrebid != null ? extPrebid.getVideo() : null;
        return extVideo != null ? extVideo.getPrimaryCategory() : null;
    }

    private ExtBidPrebid toExtBidPrebid(ObjectNode ext) {
        try {
            return jacksonMapper.mapper().treeToValue(ext, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Queries external source for bid's category.
     */
    private Future<CategoryBidContext> fetchCategory(BidderBid bidderBid,
                                                     String bidder,
                                                     String primaryAdServer,
                                                     String publisher,
                                                     String category,
                                                     Timeout timeout) {

        final String bidId = bidderBid.getBid().getId();

        return applicationSettings.getCategories(primaryAdServer, publisher, timeout)
                .map(fetchedCategories -> findAndValidateCategory(
                        fetchedCategories, category, bidId, bidder, primaryAdServer, publisher))

                .recover(throwable -> wrapWithRejectedBidException(bidId, bidder, throwable))

                .map(fetchedCategory -> CategoryBidContext.of(bidderBid, bidder, fetchedCategory));
    }

    /**
     * Throws {@link RejectedBidException} when fetched category is null or empty.
     */
    private static String findAndValidateCategory(Map<String, String> fetchedCategories,
                                                  String category,
                                                  String bidId,
                                                  String bidder,
                                                  String primaryAdServer,
                                                  String publisher) {

        if (MapUtils.isEmpty(fetchedCategories)) {
            throw new RejectedBidException(bidId, bidder,
                    String.format("Category mapping data for primary ad server: '%s', publisher: '%s' "
                            + "not found", primaryAdServer, publisher));
        }

        final String categoryId = fetchedCategories.get(category);
        if (StringUtils.isEmpty(categoryId)) {
            throw new RejectedBidException(bidId, bidder,
                    String.format("Category mapping data for primary ad server: '%s', publisher: '%s' "
                            + "does not contain category for cat = '%s'", primaryAdServer, publisher, category));
        }
        return categoryId;
    }

    /**
     * Wraps throwable with {@link RejectedBidException} to contain information about bidId and bidder
     * caused this exception.
     */
    private Future<String> wrapWithRejectedBidException(String bidId, String bidder, Throwable throwable) {
        return Future.failedFuture(new RejectedBidException(bidId, bidder, throwable.getMessage()));
    }

    /**
     * Collects categories from all futures working on retrieving category from external sources.
     * Drops corresponding bid for failed attempt to request category.
     */
    private static void collectCategoryFetchResults(CompositeFuture compositeFuture,
                                                    Promise<List<CategoryBidContext>> resultPromise,
                                                    List<RejectedBid> rejectedBids) {

        final List<CategoryBidContext> categoryBidContexts = new ArrayList<>();

        for (int i = 0; i < compositeFuture.list().size(); i++) {
            final Object o = compositeFuture.resultAt(i);
            if (o != null) {
                categoryBidContexts.add((CategoryBidContext) o);
            } else {
                final RejectedBidException rejectedBidException = (RejectedBidException) compositeFuture.cause(i);
                rejectedBids.add(rejectedBidException.getBid());
            }
        }

        resultPromise.complete(categoryBidContexts);
    }

    /**
     * Creates duration dropping bids with duplicated categories.
     */
    private CategoryMappingResult resolveBidsCategoriesDurations(List<BidderResponse> bidderResponses,
                                                                 List<CategoryBidContext> categoryBidContexts,
                                                                 BidRequest bidRequest,
                                                                 ExtRequestTargeting targeting,
                                                                 boolean withCategory,
                                                                 List<RejectedBid> rejectedBids) {

        final PriceGranularity priceGranularity = resolvePriceGranularity(targeting);
        final List<Integer> durations = ListUtils.emptyIfNull(targeting.getDurationrangesec()).stream()
                .sorted().collect(Collectors.toList());

        final List<String> errors = new ArrayList<>();
        final Map<String, Map<String, ExtDealTier>> impIdToBiddersDealTear = isSupportedForDeals(bidRequest)
                ? extractDealTierPerImpAndBidder(bidRequest.getImp(), errors)
                : Collections.emptyMap();

        final boolean appendBidderNames = BooleanUtils.toBooleanDefaultIfNull(targeting.getAppendbiddernames(), false);
        final Map<String, Set<CategoryBidContext>> uniqueCatKeysToCategoryBids = categoryBidContexts.stream()
                .map(categoryBidContext -> enrichCategoryBidContext(categoryBidContext, durations, priceGranularity,
                        withCategory, appendBidderNames, impIdToBiddersDealTear, rejectedBids))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(CategoryBidContext::getCategoryUniqueKey,
                        Collectors.mapping(Function.identity(), Collectors.toSet())));

        rejectedBids.addAll(collectRejectedDuplicatedBids(uniqueCatKeysToCategoryBids));
        errors.addAll(rejectedBids.stream().map(RejectedBid::getErrorMessage).collect(Collectors.toList()));

        return CategoryMappingResult.of(
                makeBidderToBidCategoryDuration(uniqueCatKeysToCategoryBids, rejectedBids),
                makeBidsSatisfiedPriority(uniqueCatKeysToCategoryBids),
                removeRejectedBids(bidderResponses, rejectedBids),
                errors);
    }

    /**
     * Returns video price granularity if exists, otherwise common price granularity.
     */
    private PriceGranularity resolvePriceGranularity(ExtRequestTargeting targeting) {
        final PriceGranularity videoPriceGranularity = resolveVideoMediaTypePriceGranularity(targeting);
        return videoPriceGranularity != null
                ? videoPriceGranularity
                : createPriceGranularity(targeting.getPricegranularity(), "pricegranularity");
    }

    /**
     * Parses video type price granularity.
     */
    private PriceGranularity resolveVideoMediaTypePriceGranularity(ExtRequestTargeting targeting) {
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = targeting.getMediatypepricegranularity();
        return extMediaTypePriceGranularity != null
                ? createPriceGranularity(extMediaTypePriceGranularity.getVideo(), "mediatypepricegranularity.video")
                : null;
    }

    /**
     * Converts {@link JsonNode} price granularity to {@link PriceGranularity}.
     */
    private PriceGranularity createPriceGranularity(JsonNode nodePriceGranularity, String path) {
        final ExtPriceGranularity extPriceGranularity = nodePriceGranularity != null && !nodePriceGranularity.isNull()
                ? parsePriceGranularity(nodePriceGranularity, path)
                : null;
        return extPriceGranularity != null ? PriceGranularity.createFromExtPriceGranularity(extPriceGranularity) : null;
    }

    /**
     * Parses {@link JsonNode} to {@link List} of {@link ExtPriceGranularity}.
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
     * Determines if deals are supported for category durations.
     */
    private static boolean isSupportedForDeals(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        return prebid != null && BooleanUtils.toBooleanDefaultIfNull(prebid.getSupportdeals(), false);
    }

    /**
     * Extracts {@link ExtDealTier}s from {@link List<Imp>} per imp per bidder.
     */
    private Map<String, Map<String, ExtDealTier>> extractDealTierPerImpAndBidder(List<Imp> imps, List<String> errors) {
        return imps.stream()
                .collect(Collectors.toMap(Imp::getId, imp -> extractBidderToDealTiers(imp, errors)));
    }

    /**
     * Extracts {@link ExtDealTier}s from {@link Imp} per bidder.
     */
    private Map<String, ExtDealTier> extractBidderToDealTiers(Imp imp, List<String> errors) {
        final ObjectNode impExt = imp.getExt();
        final Map<String, DealTierContainer> bidderToImpExtDealTier =
                jacksonMapper.mapper().convertValue(impExt, EXT_IMP_DEAL_TIER_REFERENCE);

        final ExtImp extImp = parseImpExt(impExt);
        final ExtImpPrebid extImpPrebid = extImp != null ? extImp.getPrebid() : null;
        final ObjectNode bidders = extImpPrebid != null ? extImpPrebid.getBidder() : null;
        final Map<String, DealTierContainer> bidderToImpExtPrebidDealTier =
                bidders != null
                        ? jacksonMapper.mapper().convertValue(bidders, EXT_IMP_DEAL_TIER_REFERENCE)
                        : Collections.emptyMap();

        bidderToImpExtPrebidDealTier.forEach((bidder, dealTierContainer)
                -> bidderToImpExtDealTier.merge(bidder, dealTierContainer, (value1, value2) -> value2));

        return bidderToImpExtDealTier.entrySet().stream()
                .filter(entry -> isValidBidder(entry.getKey()))
                .filter(entry -> isValidExtDealTier(entry.getValue(), entry.getKey(), imp.getId(), errors))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getDealTier()));
    }

    private ExtImp parseImpExt(ObjectNode impExt) {
        try {
            return jacksonMapper.mapper().treeToValue(impExt, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Failed to decode imp.ext");
        }
    }

    /**
     * Returns true when bidder parameter is valid bidder name.
     */
    private static boolean isValidBidder(String bidder) {
        return Ortb2ImplicitParametersResolver.isImpExtBidderField(bidder);
    }

    /**
     * Returns true if {@link DealTierContainer} is not null and has valid {@link ExtDealTier} fields.
     */
    private static boolean isValidExtDealTier(DealTierContainer dealTierContainer,
                                              String bidder,
                                              String impId,
                                              List<String> errors) {

        final ExtDealTier dealTier = ObjectUtil.getIfNotNull(dealTierContainer, DealTierContainer::getDealTier);
        if (dealTier == null) {
            errors.add(String.format("DealTier configuration not defined for bidder '%s', imp ID '%s'", bidder, impId));
            return false;
        }

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
     * Returns true if there is no bid with bidId and bidder in rejected list.
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
    private CategoryBidContext enrichCategoryBidContext(CategoryBidContext categoryBidContext,
                                                        List<Integer> durations,
                                                        PriceGranularity priceGranularity,
                                                        boolean withCategory,
                                                        boolean appendBidderName,
                                                        Map<String, Map<String, ExtDealTier>> impToBiddersDealTier,
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

        final BigDecimal price = CpmRange.fromCpmAsNumber(bid.getPrice(), priceGranularity);
        final String rowPrice = CpmRange.format(price, priceGranularity.getPrecision());
        final String category = categoryBidContext.getCategory();
        final String categoryUniqueKey = createCategoryUniqueKey(withCategory, category, rowPrice, duration);

        final Map<String, ExtDealTier> impsDealTiers = impToBiddersDealTier.get(bid.getImpid());
        final ExtDealTier dealTier = impsDealTiers != null ? impsDealTiers.get(bidder) : null;
        final int dealPriority = bidderBid.getDealPriority() != null ? bidderBid.getDealPriority() : 0;
        final boolean satisfiedPriority = dealTier != null && dealPriority >= dealTier.getMinDealTier();

        final String categoryDuration = createCategoryDuration(rowPrice, category, duration, satisfiedPriority,
                dealTier, withCategory, appendBidderName, bidder);

        return categoryBidContext.toBuilder()
                .categoryUniqueKey(categoryUniqueKey)
                .satisfiedPriority(satisfiedPriority)
                .categoryDuration(categoryDuration)
                .price(price)
                .build();
    }

    /**
     * Resolves video duration for bid.
     */
    private Integer resolveDuration(List<Integer> durations, BidderBid bidderBid, String bidder) {
        final ExtBidPrebidVideo video = bidderBid.getVideoInfo();
        final Integer videoDuration = video != null ? video.getDuration() : null;
        final int duration = videoDuration != null ? videoDuration : 0;
        if (CollectionUtils.isEmpty(durations)) {
            return duration;
        }

        final String bidId = bidderBid.getBid().getId();

        final int maxDuration = durations.get(durations.size() - 1);
        if (duration > maxDuration) {
            throw new RejectedBidException(bidId, bidder,
                    String.format("Bid duration '%s' exceeds maximum '%s'", duration, maxDuration));
        }

        return durations.stream()
                .filter(targetingDuration -> duration <= targetingDuration)
                .findFirst()
                .orElseThrow(() -> new RejectedBidException(bidId, bidder, "Duration is not in targeting range"));
    }

    /**
     * Creates category key which used for finding duplicated bids.
     */
    private String createCategoryUniqueKey(boolean withCategory, String category, String price, int duration) {
        return withCategory ? category : String.format("%s_%ds", price, duration);
    }

    private static String createCategoryDuration(String price,
                                                 String category,
                                                 int duration,
                                                 boolean satisfiedPriority,
                                                 ExtDealTier dealTier,
                                                 boolean withCategory,
                                                 boolean appendBidderName,
                                                 String bidder) {

        final String categoryPrefix = dealTier != null && satisfiedPriority
                ? String.format("%s%d", dealTier.getPrefix(), dealTier.getMinDealTier())
                : price;
        final String categoryDuration = withCategory
                ? String.format("%s_%s_%ds", categoryPrefix, category, duration)
                : String.format("%s_%ds", categoryPrefix, duration);

        return appendBidderName ? String.format("%s_%s", categoryDuration, bidder) : categoryDuration;
    }

    /**
     * Collects all bids defined as duplicated to collection and creates {@link RejectedBid} with reason.
     */
    private static Set<RejectedBid> collectRejectedDuplicatedBids(
            Map<String, Set<CategoryBidContext>> categoryToDuplicatedCategoryBids) {

        return categoryToDuplicatedCategoryBids.values().stream()
                .filter(categoryBids -> categoryBids.size() > 1)
                .flatMap(CategoryMappingService::getDuplicatedForCategory)
                .map(categoryBidContext -> RejectedBid.of(
                        extractBidId(categoryBidContext),
                        categoryBidContext.getBidder(),
                        "Bid was deduplicated"))
                .collect(Collectors.toSet());
    }

    /**
     * Creates mapping between bidder and its map of bidId to category duration.
     */
    private static Map<Bid, String> makeBidderToBidCategoryDuration(
            Map<String, Set<CategoryBidContext>> categoryToBidsWithBidder,
            List<RejectedBid> rejectedBids) {

        return categoryToBidsWithBidder.values().stream()
                .flatMap(Collection::stream)
                .filter(categoryBidContext -> isNotRejected(
                        extractBidId(categoryBidContext),
                        categoryBidContext.getBidder(),
                        rejectedBids))
                .collect(Collectors.toMap(
                        categoryBidContext -> categoryBidContext.getBidderBid().getBid(),
                        CategoryBidContext::getCategoryDuration));
    }

    private Map<Bid, Boolean> makeBidsSatisfiedPriority(
            Map<String, Set<CategoryBidContext>> uniqueCatKeysToCategoryBids) {

        return uniqueCatKeysToCategoryBids.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        categoryBidContext -> categoryBidContext.getBidderBid().getBid(),
                        CategoryBidContext::isSatisfiedPriority));
    }

    /**
     * Removes rejected bids from  {@link List<BidderResponse>}.
     */
    private static List<BidderResponse> removeRejectedBids(List<BidderResponse> bidderResponses,
                                                           List<RejectedBid> rejectedBids) {

        final Map<String, List<String>> bidderToRejectedBidIds = rejectedBids.stream()
                .collect(Collectors.groupingBy(RejectedBid::getBidder,
                        Collectors.mapping(RejectedBid::getBidId, Collectors.toList())));

        return bidderResponses.stream()
                .map(bidderResponse -> bidderToRejectedBidIds.containsKey(bidderResponse.getBidder())
                        ? removeRejectedBids(bidderResponse, bidderToRejectedBidIds.get(bidderResponse.getBidder()))
                        : bidderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Remove rejected bids from {@link BidderResponse}.
     */
    private static BidderResponse removeRejectedBids(BidderResponse bidderResponse, List<String> rejectedBidIds) {
        final String bidder = bidderResponse.getBidder();

        final List<BidderBid> survivedBidderBids = bidderResponse.getSeatBid().getBids().stream()
                .filter(bidderBid -> !rejectedBidIds.contains(bidderBid.getBid().getId()))
                .collect(Collectors.toList());

        final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();
        return BidderResponse.of(bidder,
                BidderSeatBid.of(survivedBidderBids, bidderSeatBid.getHttpCalls(), bidderSeatBid.getErrors()),
                bidderResponse.getResponseTime());
    }

    /**
     * Creates {@link Stream<CategoryBidContext>} of duplicated bids,
     * adding bids with price lower than bid with max price.
     */
    private static Stream<CategoryBidContext> getDuplicatedForCategory(Set<CategoryBidContext> categoryBidContexts) {
        final CategoryBidContext highestPriceBid = categoryBidContexts.stream()
                .max(Comparator.comparing(CategoryBidContext::getPrice))
                .orElseThrow(() -> new PreBidException("Can't find bid with highest price."));

        return categoryBidContexts.stream()
                .filter(categoryBidContext -> ObjectUtils.notEqual(highestPriceBid, categoryBidContext));
    }

    private static String extractBidId(CategoryBidContext categoryBidContext) {
        return categoryBidContext.getBidderBid().getBid().getId();
    }

    private static class RejectedBidException extends RuntimeException {

        private final RejectedBid rejectedBid;

        RejectedBidException(String bidId, String bidder, String error) {
            super(error);
            rejectedBid = RejectedBid.of(bidId, bidder, error);
        }

        public RejectedBid getBid() {
            return rejectedBid;
        }
    }

    /**
     * Holder of information about rejected bid.
     */
    @Value
    private static class RejectedBid {

        String bidId;

        String bidder;

        String errorMessage;

        private static RejectedBid of(String bidId, String bidder, String errorMessage) {
            return new RejectedBid(bidId, bidder,
                    String.format("Bid rejected [bidder: %s, bid ID: %s] with a reason: %s",
                            bidder, bidId, errorMessage));
        }
    }

    /**
     * Holder for bid's category information.
     */
    @Value
    @Builder(toBuilder = true)
    private static class CategoryBidContext {

        public static CategoryBidContext of(BidderBid bidderBid, String bidder, String category) {
            return CategoryBidContext.builder()
                    .bidderBid(bidderBid)
                    .bidder(bidder)
                    .category(category)
                    .build();
        }

        BidderBid bidderBid;

        String bidder;

        String category;

        String categoryDuration;

        String categoryUniqueKey;

        BigDecimal price;

        boolean satisfiedPriority;
    }

    @Value(staticConstructor = "of")
    private static class DealTierContainer {

        @JsonProperty("dealTier")
        ExtDealTier dealTier;
    }
}
