package org.prebid.server.deals;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
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
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class DealsPopulatorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LineItemService lineItemService;

    @Mock
    private CriteriaLogManager criteriaLogManager;

    private DealsPopulator dealsPopulator;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2019-10-10T00:01:00Z"), ZoneOffset.UTC);

    @Before
    public void setUp() {
        given(lineItemService.accountHasDeals(any())).willReturn(true);
        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(emptyList()));

        dealsPopulator = new DealsPopulator(lineItemService, jacksonMapper, criteriaLogManager);
    }

    @Test
    public void populateDealsInfoShouldReturnOriginalContextIfAccountHasNoDeals() {
        // given
        given(lineItemService.accountHasDeals(any())).willReturn(false);

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(givenAccount(identity()))
                .bidRequest(givenBidRequest(identity()))
                .build();

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result).isSameAs(auctionContext);
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDeals() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder()
                                .lineItemId("lineItemId")
                                .extLineItemId("extLineItemId")
                                .source("bidder")
                                .dealId("dealId")
                                .build(),
                        null, null, ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .pmp(Pmp.builder()
                                .deals(singletonList(Deal.builder().id("existingDealId").build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .containsOnly(
                        Deal.builder().id("existingDealId").build(),
                        Deal.builder()
                                .id("dealId")
                                .ext(mapper.valueToTree(
                                        ExtDeal.of(ExtDealLine.of("lineItemId", "extLineItemId", null, "bidder"))))
                                .build());
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDealsAndAddLineItemSizesIfSizesIntersectionMatched() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder().sizes(singletonList(LineItemSize.of(200, 20))).build(), null, null,
                        ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(100).h(10).build(),
                                        Format.builder().w(200).h(20).build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .extracting(Deal::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtDeal.class))
                .containsOnly(ExtDeal.of(
                        ExtDealLine.of(null, null, singletonList(Format.builder().w(200).h(20).build()), null)));
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDealsAndNotAddLineItemSizesIfSizesIntersectionNotMatched() {
        // given
        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder().sizes(singletonList(LineItemSize.of(200, 20))).build(), null, null,
                        ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(100).h(10).build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .extracting(Deal::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtDeal.class))
                .containsOnly(ExtDeal.of(ExtDealLine.of(null, null, null, null)));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> customizer) {
        return customizer.apply(BidRequest.builder()).build();
    }

    private static Account givenAccount(Function<Account.AccountBuilder, Account.AccountBuilder> customizer) {
        return customizer.apply(Account.builder().id("accountId")).build();
    }

    private static AuctionContext givenAuctionContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .debugWarnings(new ArrayList<>())
                .build();
    }
}
