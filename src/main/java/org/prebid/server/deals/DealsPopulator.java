package org.prebid.server.deals;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DealsPopulator {

    private static final Logger logger = LoggerFactory.getLogger(DealsPopulator.class);

    private final LineItemService lineItemService;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;

    public DealsPopulator(LineItemService lineItemService,
                          JacksonMapper mapper,
                          CriteriaLogManager criteriaLogManager) {

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
    }

    public Future<AuctionContext> populate(AuctionContext context) {
        final boolean accountHasDeals = lineItemService.accountHasDeals(context);
        final Future<AuctionContext> future = Future.succeededFuture(context);

        return accountHasDeals
                ? future.map(this::matchAndPopulateDeals)
                : future;
    }

    /**
     * Fetches {@link MatchLineItemsResult} for each {@link Imp} and enriches it with {@link Deal}s.
     */
    private AuctionContext matchAndPopulateDeals(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final List<Imp> updatedImps = new ArrayList<>();
        boolean isImpsUpdated = false;

        for (Imp imp : bidRequest.getImp()) {
            final MatchLineItemsResult matchResult = lineItemService.findMatchingLineItems(auctionContext, imp);
            final List<LineItem> lineItems = matchResult.getLineItems();

            lineItems.forEach(lineItem -> criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(),
                    lineItem.getLineItemId(),
                    "LineItem %s is ready to be served".formatted(lineItem.getLineItemId()), logger::debug));

            final Imp updatedImp = lineItems.isEmpty() ? imp : enrichImpWithDeals(imp, lineItems);
            isImpsUpdated |= imp != updatedImp;

            updatedImps.add(updatedImp);
        }

        final AuctionContext result;
        if (!isImpsUpdated) {
            result = auctionContext;
        } else {
            final BidRequest updatedBidRequest = bidRequest.toBuilder().imp(updatedImps).build();
            result = auctionContext.toBuilder().bidRequest(updatedBidRequest).build();
        }
        return result;
    }

    /**
     * Populates request.imp[].pmp object:
     * <p>
     * - injects dealIds from selected {@link LineItem}s to corresponding request.imp[].pmp.deals[].id.
     * <p>
     * - stores {@link LineItem} information in request.imp[].pmp.deals[].ext.line object.
     */
    private Imp enrichImpWithDeals(Imp imp, List<LineItem> lineItems) {
        final List<Deal> deals = lineItems.stream()
                .map(lineItem -> toDeal(imp, lineItem))
                .toList();

        return impWithPopulatedDeals(imp, deals);
    }

    /**
     * Creates {@link Deal} from the given {@link LineItem}.
     */
    private Deal toDeal(Imp imp, LineItem lineItem) {
        return Deal.builder()
                .id(lineItem.getDealId())
                .ext(mapper.mapper().valueToTree(ExtDeal.of(toExtDealLine(imp, lineItem))))
                .build();
    }

    private static ExtDealLine toExtDealLine(Imp imp, LineItem lineItem) {
        final List<Format> formats = ObjectUtil.getIfNotNull(imp.getBanner(), Banner::getFormat);
        final List<LineItemSize> lineItemSizes = lineItem.getSizes();

        final List<Format> lineSizes;
        if (CollectionUtils.isNotEmpty(formats) && CollectionUtils.isNotEmpty(lineItemSizes)) {
            final List<Format> matchedSizes = lineItemSizes.stream()
                    .filter(size -> formatsContainLineItemSize(formats, size))
                    .map(size -> Format.builder().w(size.getW()).h(size.getH()).build())
                    .toList();
            lineSizes = CollectionUtils.isNotEmpty(matchedSizes) ? matchedSizes : null;
        } else {
            lineSizes = null;
        }

        return ExtDealLine.of(lineItem.getLineItemId(), lineItem.getExtLineItemId(), lineSizes, lineItem.getSource());
    }

    /**
     * Returns true if the given {@link LineItemSize} is found in a list of imp.banner {@link Format}s.
     */
    private static boolean formatsContainLineItemSize(List<Format> formats, LineItemSize lineItemSize) {
        return formats.stream()
                .anyMatch(format -> Objects.equals(format.getW(), lineItemSize.getW())
                        && Objects.equals(format.getH(), lineItemSize.getH()));
    }

    /**
     * Returns {@link Imp} with populated {@link Deal}s.
     */
    private static Imp impWithPopulatedDeals(Imp imp, List<Deal> deals) {
        final Pmp pmp = imp.getPmp();
        final List<Deal> existingDeals = ListUtils.emptyIfNull(pmp != null ? pmp.getDeals() : null);

        final List<Deal> combinedDeals = Stream.concat(existingDeals.stream(), deals.stream())
                .toList();

        final Pmp.PmpBuilder pmpBuilder = pmp != null ? pmp.toBuilder() : Pmp.builder();
        final Pmp updatedPmp = pmpBuilder.deals(combinedDeals).build();
        return imp.toBuilder().pmp(updatedPmp).build();
    }
}
