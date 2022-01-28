package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class BasicPriceFloorProcessorTest extends VertxTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private PriceFloorFetcher priceFloorFetcher;
    @Mock
    private PriceFloorResolver floorResolver;
    @Mock
    private JsonMerger jsonMerger;
    @Mock
    private JacksonMapper mapper;

    private BasicPriceFloorProcessor priceFloorProcessor;

    @Before
    public void setUp() {
        priceFloorProcessor = new BasicPriceFloorProcessor(priceFloorFetcher, floorResolver, jsonMerger, mapper);
    }

    @Test
    public void shouldDoNothingIfPriceFloorsDisabledForAccount() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                accountFloors -> accountFloors.enabled(false),
                givenFloors(identity()));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(result).isSameAs(auctionContext);
    }

    @Test
    public void shouldDoNothingIfPriceFloorsDisabledForRequest() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                identity(),
                givenFloors(floors -> floors.enabled(false)));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(result).isSameAs(auctionContext);
    }

    @Test
    public void shouldUseFloorsFromProviderIfPresent() {
        // given
        final PriceFloorRules requestFloors = givenFloors(identity());
        final AuctionContext auctionContext = givenAuctionContext(identity(), requestFloors);

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ONE));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        given(jsonMerger.merge(any(), any(), any())).willReturn(providerFloors);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        verify(jsonMerger).merge(same(providerFloors), same(requestFloors), any());

        assertThat(result)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getFloors)
                .isEqualTo(givenFloors(floors -> floors
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.provider)));
    }

    @Test
    public void shouldUseFloorsFromRequestIfProviderFloorsMissing() {
        // given
        final PriceFloorRules requestFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ONE));
        final AuctionContext auctionContext = givenAuctionContext(identity(), requestFloors);

        given(priceFloorFetcher.fetch(any())).willReturn(null);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        verifyNoInteractions(jsonMerger);

        assertThat(result)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getFloors)
                .isEqualTo(givenFloors(floors -> floors
                        .floorMin(BigDecimal.ONE)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldTolerateMissingRequestAndProviderFloors() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity(), null);

        given(priceFloorFetcher.fetch(any())).willReturn(null);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        verifyNoInteractions(jsonMerger);

        assertThat(result)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getFloors)
                .isEqualTo(givenFloors(floors -> floors
                        .location(PriceFloorLocation.none)));
    }

    private static AuctionContext givenAuctionContext(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> accountFloorsCustomizer,
            PriceFloorRules floors) {

        return AuctionContext.builder()
                .account(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .priceFloors(givenAccountPriceFloorsConfig(accountFloorsCustomizer))
                                .build())
                        .build())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .floors(floors)
                                .build()))
                        .build())
                .build();
    }

    private static AccountPriceFloorsConfig givenAccountPriceFloorsConfig(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> configCustomizer) {

        return configCustomizer.apply(AccountPriceFloorsConfig.builder()).build();
    }

    public static PriceFloorRules givenFloors(
            UnaryOperator<PriceFloorRules.PriceFloorRulesBuilder> floorsCustomizer) {

        return floorsCustomizer.apply(PriceFloorRules.builder()).build();
    }
}
