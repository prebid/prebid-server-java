package org.prebid.server.deals;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DealsProcessorTest extends VertxTest {

    private DealsProcessor dealsProcessor;

    @Before
    public void setUp() {
        dealsProcessor = new DealsProcessor(jacksonMapper);
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldRemovePgDealsOnlyBiddersWithoutDealsWhenPmpIsNull() {
        // given
        final ObjectNode extImp = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set(
                                        "rubicon", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE))
                                .set("appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE))));

        final Imp imp = Imp.builder().pmp(null).ext(extImp).build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, null);

        // then
        assertThat(result).isEqualTo(Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode().set(
                                        "appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE)))))
                .build());
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldRemovePgDealsOnlyBiddersWithoutDealsWhenPmpDealsIsEmpty() {
        // given
        final ObjectNode extImp = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set(
                                        "rubicon", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE))
                                .set("appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE))));

        final Imp imp = Imp.builder()
                .pmp(Pmp.builder().deals(emptyList()).build())
                .ext(extImp)
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, null);

        // then
        assertThat(result).isEqualTo(Imp.builder()
                .pmp(Pmp.builder().deals(emptyList()).build())
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode().set(
                                        "appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE)))))
                .build());
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldRemovePgDealsOnlyBiddersWithoutDeals() {
        // given
        final ObjectNode extImp = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set(
                                        "rubicon", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE))
                                .set("appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE))));

        final Imp imp = Imp.builder()
                .pmp(Pmp.builder()
                        .deals(singletonList(
                                Deal.builder()
                                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                null, null, null, "appnexus"))))
                                        .build()))
                        .build())
                .ext(extImp)
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));

        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(
                auctionContext, imp, BidderAliases.of(null, null, mock(BidderCatalog.class)));

        // then
        assertThat(result).isEqualTo(Imp.builder()
                .pmp(Pmp.builder()
                        .deals(singletonList(
                                Deal.builder()
                                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                null, null, null, "appnexus"))))
                                        .build()))
                        .build())
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode().set(
                                        "appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE)))))
                .build());
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldNotRemoveBiddersWithoutDealsIfPgDealsOnlyFlagWasNotDefined() {
        // given
        final Imp imp = Imp.builder()
                .ext(mapper.createObjectNode().set("appnexus", mapper.createObjectNode()))
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, null);

        // then
        assertThat(result).isEqualTo(Imp.builder()
                .ext(mapper.createObjectNode().set("appnexus", mapper.createObjectNode()))
                .build());
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldRemovePgDealsOnlyBiddersForResolvedBidderFromAliases() {
        // given
        final ExtImp impExt = ExtImp.of(
                ExtImpPrebid.builder()
                        .bidder(mapper.createObjectNode()
                                .<ObjectNode>set("appnexusAlias", mapper.createObjectNode()
                                        .set("pgdealsonly", BooleanNode.TRUE))
                                .set("rubiconAlias", mapper.createObjectNode()
                                        .set("pgdealsonly", BooleanNode.FALSE)))
                        .build(),
                null);

        final Imp imp = Imp.builder().ext(mapper.valueToTree(impExt)).build();

        final BidderAliases aliases = BidderAliases.of(
                Map.of(
                        "appnexusAlias", "appnexus",
                        "rubiconAlias", "rubicon"),
                null,
                mock(BidderCatalog.class));
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));

        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, aliases);

        // then
        assertThat(result).isEqualTo(Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode().set(
                                        "rubiconAlias",
                                        mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE)))))
                .build());
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldRemovePgDealsOnlyBiddersWithoutDealsAndAddDebugMessage() {
        // given
        final ObjectNode extImp = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set(
                                        "rubicon", mapper.createObjectNode().set("pgdealsonly", BooleanNode.TRUE))
                                .set("appnexus", mapper.createObjectNode().set("pgdealsonly", BooleanNode.FALSE))));

        final Imp imp = Imp.builder()
                .id("impId")
                .pmp(Pmp.builder().deals(emptyList()).build())
                .ext(extImp)
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, null);

        // then
        assertThat(auctionContext.getDebugWarnings())
                .containsExactly("Not calling rubicon bidders for impression impId"
                        + " due to pgdealsonly flag and no available PG line items.");
    }

    @Test
    public void removePgDealsOnlyBiddersWithoutDealsShouldReturnNullIfRemoveAllBidders() {
        // given
        final Imp imp = Imp.builder()
                .id("impId1")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode()
                                        .set("rubicon", mapper.createObjectNode()
                                                .set("pgdealsonly", BooleanNode.TRUE)))
                                .build(),
                        null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final Imp result = dealsProcessor.removePgDealsOnlyBiddersWithoutDeals(auctionContext, imp, null);

        // then
        assertThat(result).isNull();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> customizer) {
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
