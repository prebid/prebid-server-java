package org.prebid.server.deals;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class DealsServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private LineItemService lineItemService;
    @Mock
    private CriteriaLogManager criteriaLogManager;
    @Mock
    private BidderCatalog bidderCatalog;

    private BidderAliases bidderAliases;
    private DealsService dealsService;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2019-10-10T00:01:00Z"), ZoneOffset.UTC);

    @Before
    public void setUp() {
        given(lineItemService.accountHasDeals(any())).willReturn(true);
        given(lineItemService.findMatchingLineItems(any(), any(), anyString(), any(), any()))
                .willReturn(MatchLineItemsResult.of(emptyList()));

        bidderAliases = BidderAliases.of(emptyMap(), emptyMap(), bidderCatalog);
        dealsService = new DealsService(lineItemService, jacksonMapper, criteriaLogManager);
    }

    @Test
    public void matchAndPopulateDealsShouldReturnOriginalBidderRequestIfAccountHasNoDeals() {
        // given
        given(lineItemService.accountHasDeals(any())).willReturn(false);

        final BidderRequest bidderRequest = givenBidderRequest(request -> request
                .imp(singletonList(givenImp(identity()))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final BidderRequest result = dealsService.matchAndPopulateDeals(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result).isEqualTo(bidderRequest.toBuilder().impIdToDeals(emptyMap()).build());
    }

    @Test
    public void matchAndPopulateDealsShouldEnrichImpWithDeals() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any(), anyString(), any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder()
                                .lineItemId("lineItemId")
                                .extLineItemId("extLineItemId")
                                .source("bidder")
                                .dealId("dealId")
                                .build(),
                        null,
                        null,
                        ZonedDateTime.now(CLOCK)))));

        final BidderRequest bidderRequest = givenBidderRequest(request -> request
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(givenImp(imp -> imp
                        .id("impId")
                        .pmp(Pmp.builder()
                                .deals(singletonList(Deal.builder().id("existingDealId").build()))
                                .build())))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final BidderRequest result = dealsService.matchAndPopulateDeals(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result).satisfies(request -> {
            assertThat(request.getImpIdToDeals())
                    .containsExactlyEntriesOf(Map.of("impId", singletonList(Deal.builder()
                            .id("dealId")
                            .ext(mapper.valueToTree(
                                    ExtDeal.of(ExtDealLine.of("lineItemId", "extLineItemId", null, "bidder"))))
                            .build())));

            assertThat(request.getBidRequest())
                    .extracting(BidRequest::getImp)
                    .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                    .extracting(Imp::getPmp)
                    .flatExtracting(Pmp::getDeals)
                    .containsExactly(
                            Deal.builder().id("existingDealId").build(),
                            Deal.builder()
                                    .id("dealId")
                                    .ext(mapper.valueToTree(
                                            ExtDeal.of(ExtDealLine.of("lineItemId", "extLineItemId", null, null))))
                                    .build());
        });
    }

    @Test
    public void matchAndPopulateDealsShouldEnrichImpWithDealsAndAddLineItemSizesIfSizesIntersectionMatched() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any(), anyString(), any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder()
                                .lineItemId("lineItemId")
                                .extLineItemId("extLineItemId")
                                .sizes(singletonList(LineItemSize.of(200, 20)))
                                .source("bidder")
                                .dealId("dealId")
                                .build(),
                        null,
                        null,
                        ZonedDateTime.now(CLOCK)))));

        final BidderRequest bidderRequest = givenBidderRequest(request -> request
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(givenImp(imp -> imp
                        .id("impId")
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(100).h(10).build(),
                                        Format.builder().w(200).h(20).build()))
                                .build())))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final BidderRequest result = dealsService.matchAndPopulateDeals(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result).satisfies(request -> {
            assertThat(request.getImpIdToDeals())
                    .containsExactlyEntriesOf(Map.of("impId", singletonList(Deal.builder()
                            .id("dealId")
                            .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                    "lineItemId",
                                    "extLineItemId",
                                    singletonList(Format.builder().w(200).h(20).build()),
                                    "bidder"))))
                            .build())));

            assertThat(request.getBidRequest())
                    .extracting(BidRequest::getImp)
                    .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                    .extracting(Imp::getPmp)
                    .flatExtracting(Pmp::getDeals)
                    .containsExactly(
                            Deal.builder()
                                    .id("dealId")
                                    .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                            "lineItemId",
                                            "extLineItemId",
                                            singletonList(Format.builder().w(200).h(20).build()),
                                            null))))
                                    .build());
        });
    }

    @Test
    public void matchAndPopulateDealsShouldEnrichImpWithDealsAndNotAddLineItemSizesIfSizesIntersectionNotMatched() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any(), anyString(), any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder()
                                .lineItemId("lineItemId")
                                .extLineItemId("extLineItemId")
                                .sizes(singletonList(LineItemSize.of(200, 20)))
                                .source("bidder")
                                .dealId("dealId")
                                .build(),
                        null,
                        null,
                        ZonedDateTime.now(CLOCK)))));

        final BidderRequest bidderRequest = givenBidderRequest(request -> request
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(givenImp(imp -> imp
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(100).h(10).build()))
                                .build())))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final BidderRequest result = dealsService.matchAndPopulateDeals(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result).satisfies(request -> {
            assertThat(request.getImpIdToDeals())
                    .containsExactlyEntriesOf(Map.of("impId", singletonList(Deal.builder()
                            .id("dealId")
                            .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                    "lineItemId",
                                    "extLineItemId",
                                    null,
                                    "bidder"))))
                            .build())));

            assertThat(request.getBidRequest())
                    .extracting(BidRequest::getImp)
                    .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                    .extracting(Imp::getPmp)
                    .flatExtracting(Pmp::getDeals)
                    .containsExactly(
                            Deal.builder()
                                    .id("dealId")
                                    .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                            "lineItemId",
                                            "extLineItemId",
                                            null,
                                            null))))
                                    .build());
        });
    }

    @Test
    public void matchAndPopulateDealsShouldFilterExistingDeals() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any(), anyString(), any(), any()))
                .willReturn(MatchLineItemsResult.of(emptyList()));

        final BidderRequest bidderRequest = givenBidderRequest(request -> request
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(givenImp(imp -> imp
                        .id("impId")
                        .pmp(Pmp.builder()
                                .deals(asList(
                                        Deal.builder().id("deal1").build(),
                                        Deal.builder()
                                                .id("deal2")
                                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                        null,
                                                        null,
                                                        null,
                                                        "anotherBidder"))))
                                                .build(),
                                        Deal.builder()
                                                .id("deal3")
                                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                        null,
                                                        null,
                                                        null,
                                                        "bidder"))))
                                                .build()))
                                .build())))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final BidderRequest result = dealsService.matchAndPopulateDeals(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result).satisfies(request -> {
            assertThat(request.getImpIdToDeals()).isEmpty();

            assertThat(request.getBidRequest())
                    .extracting(BidRequest::getImp)
                    .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                    .extracting(Imp::getPmp)
                    .flatExtracting(Pmp::getDeals)
                    .containsExactly(
                            Deal.builder().id("deal1").build(),
                            Deal.builder().id("deal3").build());
        });
    }

    @Test
    public void removePgDealsOnlyImpsWithoutDealsShouldRemovePgDealsOnlyImpsWithoutMatchedDeals() {
        // given
        final List<AuctionParticipation> auctionParticipations = asList(
                givenAuctionParticipation(givenBidderRequest(
                        "bidder1",
                        request -> request.imp(asList(
                                givenImp(imp -> imp.id("imp1").ext(mapper.createObjectNode())),
                                givenImp(imp -> imp.id("imp2").ext(mapper.createObjectNode())))),
                        null)),
                givenAuctionParticipation(givenBidderRequest(
                        "bidder2",
                        request -> request.imp(asList(
                                givenImp(imp -> imp
                                        .id("imp1")
                                        .ext(mapper.valueToTree(Map.of("bidder", Map.of("pgdealsonly", true))))),
                                givenImp(imp -> imp
                                        .id("imp2")
                                        .ext(mapper.valueToTree(Map.of("bidder", Map.of("pgdealsonly", true))))))),
                        emptyMap())),
                givenAuctionParticipation(givenBidderRequest(
                        "bidder3",
                        request -> request.imp(asList(
                                givenImp(imp -> imp
                                        .id("imp1")
                                        .ext(mapper.valueToTree(Map.of("bidder", Map.of("pgdealsonly", true))))),
                                givenImp(imp -> imp
                                        .id("imp2")
                                        .ext(mapper.valueToTree(Map.of("bidder", Map.of("pgdealsonly", true))))))),
                        singletonMap("imp2", singletonList(Deal.builder().build())))));
        final AuctionContext auctionContext = givenAuctionContext(identity());

        // when
        final List<AuctionParticipation> result = DealsService.removePgDealsOnlyImpsWithoutDeals(
                auctionParticipations, auctionContext);

        // then
        assertThat(result).containsExactly(
                givenAuctionParticipation(givenBidderRequest(
                        "bidder1",
                        request -> request.imp(asList(
                                givenImp(imp -> imp.id("imp1").ext(mapper.createObjectNode())),
                                givenImp(imp -> imp.id("imp2").ext(mapper.createObjectNode())))),
                        null)),
                givenAuctionParticipation(givenBidderRequest(
                        "bidder3",
                        request -> request.imp(singletonList(
                                givenImp(imp -> imp
                                        .id("imp2")
                                        .ext(mapper.valueToTree(Map.of("bidder", Map.of("pgdealsonly", true))))))),
                        singletonMap("imp2", singletonList(Deal.builder().build())))));
        assertThat(auctionContext.getDebugWarnings()).containsExactly(
                """
                        Not calling bidder2 bidder for impressions imp1, imp2 \
                        due to pgdealsonly flag and no available PG line items.""",
                """
                        Not calling bidder3 bidder for impressions imp1 \
                        due to pgdealsonly flag and no available PG line items.""");
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> customizer) {
        return customizer.apply(Imp.builder()).build();
    }

    private static BidderRequest givenBidderRequest(UnaryOperator<BidRequest.BidRequestBuilder> customizer) {
        return BidderRequest.builder()
                .bidder("bidder")
                .bidRequest(customizer.apply(BidRequest.builder()).build())
                .build();
    }

    private static BidderRequest givenBidderRequest(String bidder,
                                                    UnaryOperator<BidRequest.BidRequestBuilder> customizer,
                                                    Map<String, List<Deal>> impIdToDeals) {

        return BidderRequest.builder()
                .bidder(bidder)
                .bidRequest(customizer.apply(BidRequest.builder()).build())
                .impIdToDeals(impIdToDeals)
                .build();
    }

    private static AuctionParticipation givenAuctionParticipation(BidderRequest bidderRequest) {
        return AuctionParticipation.builder().bidderRequest(bidderRequest).build();
    }

    private static AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> customizer) {
        return AuctionContext.builder()
                .account(customizer.apply(Account.builder().id("accountId")).build())
                .debugWarnings(new ArrayList<>())
                .build();
    }
}
