package org.prebid.server.deals;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.deals.lineitem.DeliveryPlan;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.deals.targeting.TargetingDefinition;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category;
import org.prebid.server.util.StreamUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Works with {@link LineItem} related information.
 */
public class LineItemService {

    private static final Logger logger = LoggerFactory.getLogger(LineItemService.class);

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String ACTIVE = "active";

    private final Comparator<LineItem> lineItemComparator = Comparator
            .comparing(LineItem::getHighestUnspentTokensClass)
            .thenComparing(LineItem::getRelativePriority)
            .thenComparing(LineItem::getCpm, Comparator.reverseOrder());

    private final int maxDealsPerBidder;
    private final TargetingService targetingService;
    private final BidderCatalog bidderCatalog;
    private final CurrencyConversionService conversionService;
    protected final ApplicationEventService applicationEventService;
    private final String adServerCurrency;
    private final Clock clock;
    private final CriteriaLogManager criteriaLogManager;

    protected final Map<String, LineItem> idToLineItems;
    protected volatile boolean isPlannerResponsive;

    public LineItemService(int maxDealsPerBidder,
                           TargetingService targetingService,
                           BidderCatalog bidderCatalog,
                           CurrencyConversionService conversionService,
                           ApplicationEventService applicationEventService,
                           String adServerCurrency,
                           Clock clock,
                           CriteriaLogManager criteriaLogManager) {

        this.maxDealsPerBidder = maxDealsPerBidder;
        this.targetingService = Objects.requireNonNull(targetingService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.conversionService = Objects.requireNonNull(conversionService);
        this.applicationEventService = Objects.requireNonNull(applicationEventService);
        this.adServerCurrency = Objects.requireNonNull(adServerCurrency);
        this.clock = Objects.requireNonNull(clock);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);

        idToLineItems = new ConcurrentHashMap<>();
    }

    /**
     * Returns {@link LineItem} by its id.
     */
    public LineItem getLineItemById(String lineItemId) {
        return idToLineItems.get(lineItemId);
    }

    /**
     * Returns true when account has at least one active {@link LineItem}.
     */
    public boolean accountHasDeals(AuctionContext auctionContext) {
        return accountHasDeals(auctionContext.getAccount().getId(), ZonedDateTime.now(clock));
    }

    /**
     * Returns true when account has at least one active {@link LineItem} in the given time.
     */
    public boolean accountHasDeals(String account, ZonedDateTime now) {
        return StringUtils.isNotEmpty(account)
                && idToLineItems.values().stream().anyMatch(lineItem -> Objects.equals(lineItem.getAccountId(), account)
                && lineItem.isActive(now));
    }

    /**
     * Finds among active Line Items those matching Imp of the OpenRTB2 request
     * taking into account Line Items’ targeting and delivery progress.
     */
    public MatchLineItemsResult findMatchingLineItems(AuctionContext auctionContext, Imp imp) {
        final ZonedDateTime now = ZonedDateTime.now(clock);
        return findMatchingLineItems(auctionContext, imp, now);
    }

    /**
     * Finds among active Line Items those matching Imp of the OpenRTB2 request
     * taking into account Line Items’ targeting and delivery progress by the given time.
     */
    protected MatchLineItemsResult findMatchingLineItems(AuctionContext auctionContext, Imp imp, ZonedDateTime now) {
        final List<LineItem> matchedLineItems = getPreMatchedLineItems(auctionContext.getAccount().getId(),
                imp, extractAliases(auctionContext.getBidRequest()))
                .stream()
                .filter(lineItem -> isTargetingMatched(lineItem, imp, auctionContext))
                .collect(Collectors.toList());
        return MatchLineItemsResult.of(postProcessMatchedLineItems(matchedLineItems, auctionContext, imp, now));
    }

    public void updateIsPlannerResponsive(boolean isPlannerResponsive) {
        this.isPlannerResponsive = isPlannerResponsive;
    }

    /**
     * Updates metadata, starts tracking new {@link LineItem}s and {@link DeliverySchedule}s
     * and remove from tracking expired.
     */
    public void updateLineItems(List<LineItemMetaData> planResponse, boolean isPlannerResponsive) {
        updateLineItems(planResponse, isPlannerResponsive, ZonedDateTime.now(clock));
    }

    public void updateLineItems(List<LineItemMetaData> planResponse, boolean isPlannerResponsive, ZonedDateTime now) {
        this.isPlannerResponsive = isPlannerResponsive;
        if (isPlannerResponsive) {
            final List<LineItemMetaData> lineItemsMetaData = ListUtils.emptyIfNull(planResponse).stream()
                    .filter(lineItemMetaData -> !isExpired(now, lineItemMetaData.getEndTimeStamp()))
                    .filter(lineItemMetaData -> Objects.equals(lineItemMetaData.getStatus(), ACTIVE))
                    .collect(Collectors.toList());

            removeInactiveLineItems(planResponse, now);
            lineItemsMetaData.forEach(lineItemMetaData -> updateLineItem(lineItemMetaData, now));
        }
    }

    public void invalidateLineItemsByIds(List<String> lineItemIds) {
        idToLineItems.entrySet().removeIf(stringLineItemEntry -> lineItemIds.contains(stringLineItemEntry.getKey()));
        logger.info("Line Items with ids {0} were removed", String.join(", ", lineItemIds));
    }

    public void invalidateLineItems() {
        final String lineItemsToRemove = String.join(", ", idToLineItems.keySet());
        idToLineItems.clear();
        logger.info("Line Items with ids {0} were removed", lineItemsToRemove);
    }

    private boolean isExpired(ZonedDateTime now, ZonedDateTime endTime) {
        return now.isAfter(endTime);
    }

    private void removeInactiveLineItems(List<LineItemMetaData> planResponse, ZonedDateTime now) {
        final Set<String> lineItemsToRemove = ListUtils.emptyIfNull(planResponse).stream()
                .filter(lineItemMetaData -> !Objects.equals(lineItemMetaData.getStatus(), ACTIVE)
                        || isExpired(now, lineItemMetaData.getEndTimeStamp()))
                .map(LineItemMetaData::getLineItemId)
                .collect(Collectors.toSet());

        idToLineItems.entrySet().stream()
                .filter(entry -> isExpired(now, entry.getValue().getEndTimeStamp()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> lineItemsToRemove));

        if (CollectionUtils.isNotEmpty(lineItemsToRemove)) {
            logger.info("Line Items {0} were dropped as expired or inactive", String.join(", ", lineItemsToRemove));
        }
        idToLineItems.entrySet().removeIf(entry -> lineItemsToRemove.contains(entry.getKey()));
    }

    protected Collection<LineItem> getLineItems() {
        return idToLineItems.values();
    }

    protected Set<String> getLineItemIds() {
        return idToLineItems.keySet();
    }

    protected void updateLineItem(LineItemMetaData lineItemMetaData, ZonedDateTime now) {
        final TargetingDefinition targetingDefinition = makeTargeting(lineItemMetaData);
        final Price normalizedPrice = normalizedPrice(lineItemMetaData);

        idToLineItems.compute(lineItemMetaData.getLineItemId(), (id, li) -> li != null
                ? li.withUpdatedMetadata(lineItemMetaData, normalizedPrice, targetingDefinition, li.getReadyAt(), now)
                : LineItem.of(lineItemMetaData, normalizedPrice, targetingDefinition, now));
    }

    public void advanceToNextPlan(ZonedDateTime now) {
        final Collection<LineItem> lineItems = idToLineItems.values();
        for (LineItem lineItem : lineItems) {
            lineItem.advanceToNextPlan(now, isPlannerResponsive);
        }
        applicationEventService.publishDeliveryUpdateEvent();
    }

    /**
     * Creates {@link TargetingDefinition} from {@link LineItemMetaData} targeting json node.
     */
    private TargetingDefinition makeTargeting(LineItemMetaData lineItemMetaData) {
        TargetingDefinition targetingDefinition;
        try {
            targetingDefinition = targetingService.parseTargetingDefinition(lineItemMetaData.getTargeting(),
                    lineItemMetaData.getLineItemId());
        } catch (TargetingSyntaxException e) {
            criteriaLogManager.log(logger, lineItemMetaData.getAccountId(), lineItemMetaData.getSource(),
                    lineItemMetaData.getLineItemId(),
                    String.format("Line item targeting parsing failed with a reason: %s", e.getMessage()),
                    logger::warn);
            targetingDefinition = null;
        }
        return targetingDefinition;
    }

    /**
     * Returns {@link Price} with converted lineItem cpm to adServerCurrency.
     */
    private Price normalizedPrice(LineItemMetaData lineItemMetaData) {
        final Price price = lineItemMetaData.getPrice();
        if (price == null) {
            return null;
        }

        final String receivedCur = price.getCurrency();
        if (StringUtils.equals(adServerCurrency, receivedCur)) {
            return price;
        }
        final BigDecimal updatedCpm = conversionService
                .convertCurrency(price.getCpm(), Collections.emptyMap(), adServerCurrency, receivedCur, null);

        return Price.of(updatedCpm, adServerCurrency);
    }

    /**
     * Checks if bidder is valid against configured bidders in {@link BidderCatalog} or aliases.
     */
    private boolean isValidActiveBidder(String bidder, Map<String, String> aliases) {
        return !bidderCatalog.isDeprecatedName(bidder)
                && (bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder));
    }

    /**
     * Returns true if collection of bidder codes contains bidder or it's alias value.
     */
    private boolean containBidderCodeConsideringAliases(List<String> bidders, String bidder,
                                                        Map<String, String> aliases) {
        return bidders.contains(bidder) || bidders.contains(aliases.get(bidder))
                || bidders.stream().anyMatch(bidderCode -> bidder.equals(aliases.get(bidderCode)));
    }

    /**
     * Return {@link List<LineItem>} matched to {@link Imp} bidders considering aliases.
     */
    private List<LineItem> getPreMatchedLineItems(String accountId, Imp imp, Map<String, String> aliases) {
        if (StringUtils.isBlank(accountId)) {
            return Collections.emptyList();
        }

        final List<LineItem> accountsLineItems = idToLineItems.values().stream()
                .filter(lineItem -> lineItem.getAccountId().equals(accountId))
                .collect(Collectors.toList());

        if (accountsLineItems.isEmpty()) {
            criteriaLogManager.log(logger, accountId,
                    String.format("There are no line items for account %s", accountId), logger::debug);
            return Collections.emptyList();
        }

        final List<String> bidders = StreamUtil.asStream(bidderParamsFromImp(imp).fieldNames())
                .filter(bidder -> isValidActiveBidder(bidder, aliases))
                .distinct()
                .collect(Collectors.toList());

        return accountsLineItems.stream()
                .filter(lineItem -> containBidderCodeConsideringAliases(bidders, lineItem.getSource(), aliases))
                .collect(Collectors.toList());
    }

    private static JsonNode bidderParamsFromImp(Imp imp) {
        return imp.getExt().get(PREBID_EXT).get(BIDDER_EXT);
    }

    /**
     * Extracts aliases from {@link BidRequest}.
     */
    private Map<String, String> extractAliases(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid prebid = extRequest != null ? extRequest.getPrebid() : null;
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        return aliases != null ? aliases : Collections.emptyMap();
    }

    /**
     * Returns true if {@link LineItem}s {@link TargetingDefinition} matches to {@link Imp}.
     * <p>
     * Updates deep debug log with matching information.
     */
    private boolean isTargetingMatched(LineItem lineItem, Imp imp, AuctionContext auctionContext) {
        final TargetingDefinition targetingDefinition = lineItem.getTargetingDefinition();
        final String accountId = auctionContext.getAccount().getId();
        final String source = lineItem.getSource();
        final String lineItemId = lineItem.getLineItemId();
        if (targetingDefinition == null) {
            deepDebug(auctionContext, Category.targeting,
                    String.format("Line Item %s targeting was not defined or has incorrect format",
                            lineItemId), accountId, source, lineItemId);
            return false;
        }

        final boolean matched = targetingService.matchesTargeting(auctionContext, imp,
                lineItem.getTargetingDefinition());
        if (matched) {
            deepDebug(auctionContext, Category.targeting,
                    String.format("Line Item %s targeting matched imp with id %s", lineItemId, imp.getId()),
                    accountId, source, lineItemId);
        } else {
            deepDebug(auctionContext, Category.targeting,
                    String.format("Line Item %s targeting did not match imp with id %s", lineItemId, imp.getId()),
                    accountId, source, lineItemId);
        }
        return matched;
    }

    /**
     * Filters {@link LineItem}s by next parameters: fcaps, readyAt, limit per bidder, same deal line items.
     */
    private List<LineItem> postProcessMatchedLineItems(List<LineItem> lineItems, AuctionContext auctionContext, Imp imp,
                                                       ZonedDateTime now) {
        final TxnLog txnLog = auctionContext.getTxnLog();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final User user = bidRequest.getUser();
        final ExtUser extUser = user.getExt();

        return lineItems.stream()
                .peek(lineItem -> txnLog.lineItemsMatchedWholeTargeting().add(lineItem.getLineItemId()))
                .filter(lineItem -> isNotFrequencyCapped(extUser.getFcapIds(), lineItem, auctionContext, txnLog))
                .filter(lineItem -> planHasTokensIfPresent(lineItem, auctionContext))
                .filter(lineItem -> isReadyAtInPast(now, lineItem, auctionContext, txnLog))
                .peek(lineItem -> txnLog.lineItemsReadyToServe().add(lineItem.getLineItemId()))
                .collect(Collectors.groupingBy(LineItem::getSource))
                .values().stream()
                .map(valueAsLineItems -> filterLineItemPerBidder(valueAsLineItems, auctionContext, imp))
                .filter(CollectionUtils::isNotEmpty)
                .peek(lineItemsForBidder -> recordInTxnSentToBidderAsTopMatch(txnLog, lineItemsForBidder))
                .flatMap(Collection::stream)
                .peek(lineItem -> txnLog.lineItemsSentToBidder().get(lineItem.getSource())
                        .add(lineItem.getLineItemId()))
                .collect(Collectors.toList());
    }

    private boolean planHasTokensIfPresent(LineItem lineItem, AuctionContext auctionContext) {
        final DeliveryPlan deliveryPlan = lineItem.getActiveDeliveryPlan();
        if (deliveryPlan == null) {
            return true;
        }
        boolean hasUnspentTokens = deliveryPlan.getDeliveryTokens().stream()
                .anyMatch(deliveryToken -> deliveryToken.getTotal() - deliveryToken.getSpent().sum() > 0);
        if (!hasUnspentTokens) {
            final String lineItemId = lineItem.getLineItemId();
            final String lineItemSource = lineItem.getSource();
            auctionContext.getTxnLog().lineItemsPacingDeferred().add(lineItemId);
            deepDebug(auctionContext, Category.pacing, String.format("Matched Line Item %s for bidder %s does not"
                            + " have unspent tokens to be served", lineItemId, lineItemSource),
                    auctionContext.getAccount().getId(), lineItemSource, lineItemId);
        }
        return hasUnspentTokens;
    }

    private boolean isReadyAtInPast(ZonedDateTime now, LineItem lineItem, AuctionContext auctionContext,
                                    TxnLog txnLog) {
        final ZonedDateTime readyAt = lineItem.getReadyAt();
        final boolean ready = readyAt != null && (readyAt.isEqual(now) || readyAt.isBefore(now));
        final String accountId = auctionContext.getAccount().getId();
        final String lineItemSource = lineItem.getSource();
        final String lineItemId = lineItem.getLineItemId();

        if (ready) {
            deepDebug(auctionContext, Category.pacing, String.format("Matched Line Item %s for bidder %s ready to "
                            + "serve. relPriority %d", lineItemId, lineItemSource, lineItem.getRelativePriority()),
                    accountId, lineItemSource, lineItemId);
        } else {
            txnLog.lineItemsPacingDeferred().add(lineItemId);
            deepDebug(auctionContext, Category.pacing, String.format("Matched Line Item %s for bidder %s not ready to"
                            + " serve. Will be ready at %s, current time is %s", lineItemId, lineItemSource,
                    readyAt != null ? UTC_MILLIS_FORMATTER.format(readyAt) : "never", UTC_MILLIS_FORMATTER.format(now)),
                    accountId, lineItemSource, lineItemId);
        }

        return ready;
    }

    /**
     * Returns false if {@link LineItem} has fcaps defined and either
     * - one of them present in the list of fcaps reached
     * - list of fcaps reached is null which means that calling User Data Store failed
     * <p>
     * Otherwise returns true
     * <p>
     * Has side effect - records discarded line item id in the transaction log
     */
    private boolean isNotFrequencyCapped(List<String> frequencyCappedByIds, LineItem lineItem,
                                         AuctionContext auctionContext, TxnLog txnLog) {
        if (CollectionUtils.isEmpty(lineItem.getFcapIds())) {
            return true;
        }

        final String lineItemId = lineItem.getLineItemId();
        final String accountId = auctionContext.getAccount().getId();
        final String lineItemSource = lineItem.getSource();

        if (frequencyCappedByIds == null) {
            txnLog.lineItemsMatchedTargetingFcapLookupFailed().add(lineItemId);
            final String message = String.format("Failed to match fcap for Line Item %s bidder %s in a reason of bad "
                    + "response from user data service", lineItemId, lineItemSource);
            deepDebug(auctionContext, Category.pacing, message, accountId, lineItemSource, lineItemId);
            criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(), lineItemId,
                    String.format("Failed to match fcap for lineItem %s in a reason of bad response from user"
                            + " data service", lineItemId), logger::debug);

            return false;
        } else if (!frequencyCappedByIds.isEmpty()) {
            final Optional<String> fcapIdOptional = lineItem.getFcapIds().stream()
                    .filter(frequencyCappedByIds::contains).findFirst();
            if (fcapIdOptional.isPresent()) {
                final String fcapId = fcapIdOptional.get();
                txnLog.lineItemsMatchedTargetingFcapped().add(lineItemId);
                final String message = String.format("Matched Line Item %s for bidder %s is "
                        + "frequency capped by fcap id %s.", lineItemId, lineItemSource, fcapId);
                deepDebug(auctionContext, Category.pacing, message, accountId, lineItemSource, lineItemId);
                criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(), lineItemId,
                        message, logger::debug);
                return false;
            }
        }

        return true;
    }

    /**
     * Filters {@link LineItem} with the same deal id and cuts {@link List<LineItem>} by maxDealsPerBidder value.
     */
    private List<LineItem> filterLineItemPerBidder(List<LineItem> lineItems, AuctionContext auctionContext, Imp imp) {
        final List<LineItem> sortedLineItems = new ArrayList<>(lineItems);
        Collections.shuffle(sortedLineItems);
        sortedLineItems.sort(lineItemComparator);

        final List<LineItem> filteredLineItems = uniqueBySentToBidderAsTopMatch(sortedLineItems, auctionContext, imp);
        updateLostToLineItems(filteredLineItems, auctionContext.getTxnLog());

        final Set<String> dealIds = new HashSet<>();
        final List<LineItem> resolvedLineItems = new ArrayList<>();
        for (final LineItem lineItem : filteredLineItems) {
            final String dealId = lineItem.getDealId();
            if (!dealIds.contains(dealId)) {
                dealIds.add(dealId);
                resolvedLineItems.add(lineItem);
            }
        }
        return resolvedLineItems.size() > maxDealsPerBidder
                ? cutLineItemsToDealMaxNumber(resolvedLineItems)
                : resolvedLineItems;
    }

    /**
     * Removes from consideration any line items that have already been sent to bidder as the TopMatch
     * in a previous impression for auction.
     */
    private List<LineItem> uniqueBySentToBidderAsTopMatch(List<LineItem> lineItems, AuctionContext auctionContext,
                                                          Imp imp) {
        final TxnLog txnLog = auctionContext.getTxnLog();
        final Set<String> topMatchedLineItems = txnLog.lineItemsSentToBidderAsTopMatch().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        final List<LineItem> result = new ArrayList<>(lineItems);
        for (LineItem lineItem : lineItems) {
            final String lineItemId = lineItem.getLineItemId();
            if (!topMatchedLineItems.contains(lineItemId)) {
                return result;
            }
            result.remove(lineItem);
            deepDebug(auctionContext, Category.cleanup, String.format(
                    "LineItem %s was dropped from imp with id %s because it was top match in another imp",
                    lineItemId, imp.getId()), auctionContext.getAccount().getId(), lineItem.getSource(), lineItemId);
        }
        return result;
    }

    private List<LineItem> cutLineItemsToDealMaxNumber(List<LineItem> resolvedLineItems) {
        resolvedLineItems.subList(maxDealsPerBidder, resolvedLineItems.size())
                .forEach(lineItem -> criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(),
                        lineItem.getLineItemId(),
                        String.format("LineItem %s was dropped by max deal per bidder limit %s",
                                lineItem.getLineItemId(), maxDealsPerBidder), logger::debug));
        return resolvedLineItems.subList(0, maxDealsPerBidder);
    }

    private void updateLostToLineItems(List<LineItem> lineItems, TxnLog txnLog) {
        for (int i = 1; i < lineItems.size(); i++) {
            final LineItem lineItem = lineItems.get(i);
            final Set<String> lostTo = lineItems.subList(0, i).stream()
                    .map(LineItem::getLineItemId)
                    .collect(Collectors.toSet());
            txnLog.lostMatchingToLineItems().put(lineItem.getLineItemId(), lostTo);
        }
    }

    private void deepDebug(AuctionContext auctionContext, Category category, String message, String accountId,
                           String bidder, String lineItemId) {
        criteriaLogManager.log(logger, accountId, bidder, lineItemId, message, logger::debug);
        auctionContext.getDeepDebugLog().add(lineItemId, category, () -> message);
    }

    private static void recordInTxnSentToBidderAsTopMatch(TxnLog txnLog, List<LineItem> lineItemsForBidder) {
        final LineItem topLineItem = lineItemsForBidder.get(0);
        txnLog.lineItemsSentToBidderAsTopMatch()
                .get(topLineItem.getSource())
                .add(topLineItem.getLineItemId());
    }
}
