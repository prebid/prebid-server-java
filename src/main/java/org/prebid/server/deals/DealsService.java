package org.prebid.server.deals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DealsService {

    private static final Logger logger = LoggerFactory.getLogger(DealsService.class);

    private static final String LINE_FIELD = "line";
    private static final String LINE_BIDDER_FIELD = "bidder";
    private static final String BIDDER_FIELD = "bidder";
    private static final String PG_DEALS_ONLY = "pgdealsonly";

    private final LineItemService lineItemService;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;

    public DealsService(LineItemService lineItemService,
                        JacksonMapper mapper,
                        CriteriaLogManager criteriaLogManager) {

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
    }

    public BidderRequest matchAndPopulateDeals(BidderRequest bidderRequest,
                                               BidderAliases aliases,
                                               AuctionContext context) {

        final String bidder = bidderRequest.getBidder();
        final BidRequest bidRequest = bidderRequest.getBidRequest();

        final Map<String, List<Deal>> impIdToDeals = match(bidRequest, bidder, aliases, context);
        final BidRequest modifiedRequest = populateDeals(bidRequest, impIdToDeals, combinerFor(bidder, aliases));

        return bidderRequest.toBuilder()
                .impIdToDeals(impIdToDeals)
                .bidRequest(modifiedRequest)
                .build();
    }

    private Map<String, List<Deal>> match(BidRequest bidRequest,
                                          String bidder,
                                          BidderAliases aliases,
                                          AuctionContext context) {

        final boolean accountHasDeals = lineItemService.accountHasDeals(context);
        if (!accountHasDeals) {
            return Collections.emptyMap();
        }

        final Map<String, List<Deal>> impIdToDeals = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final MatchLineItemsResult matchResult = lineItemService.findMatchingLineItems(
                    bidRequest, imp, bidder, aliases, context);
            final List<LineItem> lineItems = matchResult.getLineItems();

            final List<Deal> deals = lineItems.stream()
                    .peek(this::logLineItem)
                    .map(lineItem -> toDeal(lineItem, imp))
                    .toList();

            if (!deals.isEmpty()) {
                impIdToDeals.put(imp.getId(), deals);
            }
        }

        return impIdToDeals;
    }

    private void logLineItem(LineItem lineItem) {
        criteriaLogManager.log(
                logger,
                lineItem.getAccountId(),
                lineItem.getSource(),
                lineItem.getLineItemId(),
                "LineItem %s is ready to be served".formatted(lineItem.getLineItemId()), logger::debug);
    }

    private Deal toDeal(LineItem lineItem, Imp imp) {
        return Deal.builder()
                .id(lineItem.getDealId())
                .ext(mapper.mapper().valueToTree(ExtDeal.of(toExtDealLine(imp, lineItem))))
                .build();
    }

    private static ExtDealLine toExtDealLine(Imp imp, LineItem lineItem) {
        final List<Format> formats = ObjectUtil.getIfNotNull(imp.getBanner(), Banner::getFormat);
        final List<LineItemSize> lineItemSizes = lineItem.getSizes();

        final List<Format> lineSizes = CollectionUtils.isNotEmpty(formats) && CollectionUtils.isNotEmpty(lineItemSizes)
                ? intersectionOf(formats, lineItemSizes)
                : null;

        return ExtDealLine.of(lineItem.getLineItemId(), lineItem.getExtLineItemId(), lineSizes, lineItem.getSource());
    }

    private static List<Format> intersectionOf(List<Format> formats, List<LineItemSize> lineItemSizes) {
        final Set<Format> formatsSet = new HashSet<>(formats);
        final Set<Format> lineItemFormatsSet = lineItemSizes.stream()
                .map(size -> Format.builder().w(size.getW()).h(size.getH()).build())
                .collect(Collectors.toSet());

        final List<Format> matchedSizes = lineItemFormatsSet.stream()
                .filter(formatsSet::contains)
                .toList();

        return CollectionUtils.isNotEmpty(matchedSizes) ? matchedSizes : null;
    }

    private static BiFunction<List<Deal>, List<Deal>, List<Deal>> combinerFor(String bidder, BidderAliases aliases) {
        return (originalDeals, matchedDeals) ->
                Stream.concat(
                                originalDeals.stream().filter(deal -> isDealCorrespondsToBidder(deal, bidder, aliases)),
                                matchedDeals.stream())
                        .map(DealsService::prepareDealForExchange)
                        .toList();
    }

    private static boolean isDealCorrespondsToBidder(Deal deal, String bidder, BidderAliases aliases) {
        final JsonNode extLineBidder = extLineBidder(deal);
        if (!isTextual(extLineBidder)) {
            return true;
        }

        return aliases.isSame(extLineBidder.textValue(), bidder);
    }

    private static JsonNode extLineBidder(Deal deal) {
        final ObjectNode ext = deal != null ? deal.getExt() : null;
        final JsonNode extLine = ext != null ? ext.get(LINE_FIELD) : null;
        return extLine != null ? extLine.get(LINE_BIDDER_FIELD) : null;
    }

    private static boolean isTextual(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isTextual();
    }

    private static Deal prepareDealForExchange(Deal deal) {
        final JsonNode extLineBidder = extLineBidder(deal);
        if (!isTextual(extLineBidder)) {
            return deal;
        }

        final ObjectNode updatedExt = deal.getExt().deepCopy();

        final ObjectNode updatedExtLine = (ObjectNode) updatedExt.get(LINE_FIELD);
        updatedExtLine.remove(LINE_BIDDER_FIELD);

        if (updatedExtLine.isEmpty()) {
            updatedExt.remove(LINE_FIELD);
        }

        return deal.toBuilder().ext(!updatedExt.isEmpty() ? updatedExt : null).build();
    }

    public static BidRequest populateDeals(BidRequest bidRequest, Map<String, List<Deal>> impIdToDeals) {
        return populateDeals(bidRequest, impIdToDeals, ListUtils::union);
    }

    private static BidRequest populateDeals(BidRequest bidRequest,
                                            Map<String, List<Deal>> impIdToDeals,
                                            BiFunction<List<Deal>, List<Deal>, List<Deal>> dealsCombiner) {

        final List<Imp> originalImps = bidRequest.getImp();
        final List<Imp> updatedImp = originalImps.stream()
                .map(imp -> populateDeals(imp, impIdToDeals.get(imp.getId()), dealsCombiner))
                .toList();

        if (updatedImp.stream().allMatch(Objects::isNull)) {
            return bidRequest;
        }

        return bidRequest.toBuilder()
                .imp(IntStream.range(0, originalImps.size())
                        .mapToObj(i -> ObjectUtils.defaultIfNull(updatedImp.get(i), originalImps.get(i)))
                        .toList())
                .build();
    }

    private static Imp populateDeals(Imp imp,
                                     List<Deal> matchedDeals,
                                     BiFunction<List<Deal>, List<Deal>, List<Deal>> dealsCombiner) {

        final Pmp pmp = imp.getPmp();
        final List<Deal> originalDeal = pmp != null ? pmp.getDeals() : null;

        final List<Deal> combinedDeals = dealsCombiner.apply(
                ListUtils.emptyIfNull(originalDeal),
                ListUtils.emptyIfNull(matchedDeals));
        if (CollectionUtils.isEmpty(combinedDeals)) {
            return null;
        }

        final Pmp.PmpBuilder pmpBuilder = pmp != null ? pmp.toBuilder() : Pmp.builder();
        return imp.toBuilder()
                .pmp(pmpBuilder.deals(combinedDeals).build())
                .build();
    }

    public static List<AuctionParticipation> removePgDealsOnlyImpsWithoutDeals(
            List<AuctionParticipation> auctionParticipations,
            AuctionContext context) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> removePgDealsOnlyImpsWithoutDeals(auctionParticipation, context))
                .filter(Objects::nonNull)
                .toList();
    }

    private static AuctionParticipation removePgDealsOnlyImpsWithoutDeals(AuctionParticipation auctionParticipation,
                                                                          AuctionContext context) {

        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final String bidder = bidderRequest.getBidder();
        final BidRequest bidRequest = bidderRequest.getBidRequest();
        final List<Imp> imps = bidRequest.getImp();

        final Set<Integer> impsIndicesToRemove = IntStream.range(0, imps.size())
                .filter(i -> isPgDealsOnly(imps.get(i)))
                .filter(i -> !havePgDeal(imps.get(i), bidderRequest.getImpIdToDeals()))
                .boxed()
                .collect(Collectors.toSet());

        if (impsIndicesToRemove.isEmpty()) {
            return auctionParticipation;
        }
        if (impsIndicesToRemove.size() == imps.size()) {
            logImpsExclusion(context, bidder, imps);
            return null;
        }

        final List<Imp> impsToRemove = new ArrayList<>();
        final List<Imp> filteredImps = new ArrayList<>();
        for (int i = 0; i < imps.size(); i++) {
            final Imp imp = imps.get(i);
            if (impsIndicesToRemove.contains(i)) {
                impsToRemove.add(imp);
            } else {
                filteredImps.add(imp);
            }
        }

        logImpsExclusion(context, bidder, impsToRemove);

        return auctionParticipation.toBuilder()
                .bidderRequest(bidderRequest.toBuilder()
                        .bidRequest(bidRequest.toBuilder()
                                .imp(filteredImps)
                                .build())
                        .build())
                .build();
    }

    private static boolean isPgDealsOnly(Imp imp) {
        final JsonNode extBidder = imp.getExt().get(BIDDER_FIELD);
        if (extBidder == null || !extBidder.isObject()) {
            return false;
        }

        final JsonNode pgDealsOnlyNode = extBidder.path(PG_DEALS_ONLY);
        return pgDealsOnlyNode.isBoolean() && pgDealsOnlyNode.asBoolean();
    }

    private static boolean havePgDeal(Imp imp, Map<String, List<Deal>> impIdToDeals) {
        return impIdToDeals != null && CollectionUtils.isNotEmpty(impIdToDeals.get(imp.getId()));
    }

    private static void logImpsExclusion(AuctionContext context,
                                         String bidder,
                                         List<Imp> imps) {

        final String impsIds = imps.stream()
                .map(Imp::getId)
                .collect(Collectors.joining(", "));
        context.getDebugWarnings().add(
                "Not calling %s bidder for impressions %s due to %s flag and no available PG line items."
                        .formatted(bidder, impsIds, PG_DEALS_ONLY));
    }
}
