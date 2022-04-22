package org.prebid.server.floors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private CurrencyConversionService conversionService;

    private BasicPriceFloorProcessor priceFloorProcessor;

    @Before
    public void setUp() {
        priceFloorProcessor = new BasicPriceFloorProcessor(
                priceFloorFetcher,
                floorResolver,
                conversionService,
                jacksonMapper);
    }

    @Test
    public void shouldSetRulesEnabledFieldToFalseIfPriceFloorsDisabledForAccount() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(floorsConfig -> floorsConfig.enabled(false)),
                givenBidRequest(
                        identity(),
                        null));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        verifyNoInteractions(priceFloorFetcher);

        assertThat(result)
                .isEqualTo(auctionContext.with(givenBidRequest(
                        identity(), PriceFloorRules.builder().enabled(false).build())));
    }

    @Test
    public void shouldSetRulesEnabledFieldToFalseIfPriceFloorsDisabledForRequest() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.enabled(false))));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        verifyNoInteractions(priceFloorFetcher);

        assertThat(result)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getFloors)
                .extracting(PriceFloorRules::getEnabled)
                .isEqualTo(false);
    }

    @Test
    public void shouldUseFloorsFromProviderIfPresent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        null));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ONE));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
    }

    @Test
    public void shouldUseFloorsFromProviderIfUseDynamicDataIsNotPresent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(null)),
                givenBidRequest(
                        identity(),
                        null));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ONE));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
    }

    @Test
    public void shouldNUseFloorsFromProviderIfUseDynamicDataIsTrue() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(true)),
                givenBidRequest(
                        identity(),
                        null));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ONE));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
    }

    @Test
    public void shouldNotUseFloorsFromProviderIfUseDynamicDataIsFalse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(false)),
                givenBidRequest(
                        identity(),
                        null));

        final PriceFloorRules providerFloors = givenFloors(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFetchStatus)
                .isEqualTo(FetchStatus.success);
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getLocation)
                .isEqualTo(PriceFloorLocation.noData);
    }

    @Test
    public void shouldMergeProviderWithRequestFloors() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .enforcement(PriceFloorEnforcement.builder().enforcePbs(false).enforceRate(100).build())
                                .floorMin(BigDecimal.ONE))));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(BigDecimal.ZERO));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .enforcement(PriceFloorEnforcement.builder().enforceRate(100).build())
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
    }

    @Test
    public void shouldReturnProviderFloorsWhenNotEnabledByRequestAndEnforceRateAndFloorPriceAreAbsent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(floorsConfig -> floorsConfig.enabled(true)),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.data(givenFloorData(identity())).enabled(null))));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors.floorMin(null).floorMinCur(null));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        final PriceFloorRules expectedResult = providerFloors.toBuilder()
                .enabled(true)
                .fetchStatus(FetchStatus.success)
                .location(PriceFloorLocation.fetch)
                .build();

        assertThat(extractFloors(result)).isEqualTo(expectedResult);
    }

    @Test
    public void shouldReturnFloorsWithFloorMinAndCurrencyFromRequestWhenPresent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .floorMin(BigDecimal.ONE)
                                .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("USD"))))));

        final PriceFloorRules providerFloors = givenFloors(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.ONE, "USD");
    }

    @Test
    public void shouldReturnFloorsWithFloorCurrencyFromRequestAndFloorMinFromProviderWhenRequestFloorMinAbsent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .floorMinCur("USD"))));

        final PriceFloorRules providerFloors = givenFloors(floors ->
                floors.floorMin(BigDecimal.ONE)
                        .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("USD"))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.ONE, "USD");
    }

    @Test
    public void shouldReturnFloorsWithConvertedToRequestCurrencyProviderFloorMin() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .floorMinCur("USD"))));

        final PriceFloorRules providerFloors = givenFloors(floors ->
                floors.floorMin(BigDecimal.ONE)
                        .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("UAH"))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));
        given(conversionService.convertCurrency(
                eq(BigDecimal.ONE),
                eq(Collections.emptyMap()),
                eq("UAH"),
                eq("USD"),
                eq(false)))
                .willReturn(BigDecimal.valueOf(2));
        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.valueOf(2), "USD");
    }

    @Test
    public void shouldReturnFloorsWithConvertedToProviderCurrencyRequestFloorMinFromDefaultUsd() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .floorMin(BigDecimal.ONE))));

        final PriceFloorRules providerFloors = givenFloors(floors ->
                floors.floorMin(BigDecimal.valueOf(2))
                        .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("UAH"))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));
        given(conversionService.convertCurrency(
                eq(BigDecimal.ONE),
                eq(Collections.emptyMap()),
                isNull(),
                eq("UAH"),
                eq(false)))
                .willReturn(BigDecimal.valueOf(2));
        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.valueOf(2), null);
    }

    @Test
    public void shouldReturnFloorsWithRequestFloorMinCurrencyAndProviderFloorMin() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .floorMinCur("USD"))));

        final PriceFloorRules providerFloors = givenFloors(floors ->
                floors.floorMin(BigDecimal.valueOf(-2))
                        .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("UAH"))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.valueOf(-2), "USD");
    }

    @Test
    public void shouldUseFloorsFromRequestIfProviderFloorsMissing() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.floorMin(BigDecimal.ONE))));

        given(priceFloorFetcher.fetch(any())).willReturn(null);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .floorMin(BigDecimal.ONE)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldTolerateMissingRequestAndProviderFloors() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        null));

        given(priceFloorFetcher.fetch(any())).willReturn(null);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getEnabled)
                .isEqualTo(true);
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getLocation)
                .isEqualTo(PriceFloorLocation.noData);
    }

    @Test
    public void shouldNotSkipFloorsIfRootSkipRateIsOff() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.skipRate(0))));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .skipRate(0)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldSkipFloorsIfRootSkipRateIsOn() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.skipRate(100))));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .skipRate(100)
                        .enabled(false)
                        .skipped(true)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldSkipFloorsIfDataSkipRateIsOn() {
        // given
        final PriceFloorData priceFloorData = givenFloorData(floorData -> floorData.skipRate(100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.skipRate(0).data(priceFloorData))));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .enabled(false)
                        .skipRate(100)
                        .data(priceFloorData)
                        .skipped(true)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldSkipFloorsIfModelGroupSkipRateIsOn() {
        // given
        final PriceFloorData priceFloorData = givenFloorData(floorData -> floorData
                .skipRate(0)
                .modelGroups(singletonList(givenModelGroup(group -> group.skipRate(100)))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.data(priceFloorData))));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .data(priceFloorData)
                        .skipRate(100)
                        .enabled(false)
                        .skipped(true)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldNotUpdateImpsIfSelectedModelGroupIsMissing() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData.modelGroups(null))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        request -> request.imp(imps),
                        requestFloors));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractImps(result))
                .isSameAs(imps);
    }

    @Test
    public void shouldUseSelectedModelGroup() {
        // given
        final PriceFloorModelGroup modelGroup = givenModelGroup(identity());
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(modelGroup)))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        request -> request.imp(singletonList(givenImp(identity()))),
                        requestFloors));

        // when
        priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        final ArgumentCaptor<PriceFloorRules> captor = ArgumentCaptor.forClass(PriceFloorRules.class);
        verify(floorResolver).resolve(any(), captor.capture(), any(), any());
        assertThat(captor.getValue())
                .extracting(PriceFloorRules::getData)
                .extracting(PriceFloorData::getModelGroups)
                .isEqualTo(singletonList(modelGroup));
    }

    @Test
    public void shouldCopyFloorProviderValueFromDataLevel() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .floorMin(BigDecimal.ONE))));

        final PriceFloorRules providerFloors = givenFloors(floors -> floors
                .data(PriceFloorData.builder().floorProvider("someProvider").build())
                .floorMin(BigDecimal.ZERO));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloors, FetchStatus.success));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getData)
                .extracting(PriceFloorData::getFloorProvider)
                .isEqualTo("someProvider");
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorProvider)
                .isEqualTo("someProvider");
    }

    @Test
    public void shouldNotUpdateImpsIfBidFloorNotResolved() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));

        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(givenModelGroup(identity()))))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        request -> request.imp(imps),
                        requestFloors));

        given(floorResolver.resolve(any(), any(), any(), any())).willReturn(null);

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractImps(result))
                .isEqualTo(imps);
    }

    @Test
    public void shouldUpdateImpsIfBidFloorResolved() {
        // given
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(givenModelGroup(identity()))))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        request -> request.imp(singletonList(givenImp(identity()))),
                        requestFloors));

        given(floorResolver.resolve(any(), any(), any(), any()))
                .willReturn(PriceFloorResult.of("rule", BigDecimal.ONE, BigDecimal.TEN, "USD"));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        final ObjectNode ext = jacksonMapper.mapper().createObjectNode();
        final ObjectNode extPrebid = jacksonMapper.mapper().createObjectNode();
        final ObjectNode extPrebidFloors = jacksonMapper.mapper().valueToTree(
                ExtImpPrebidFloors.of("rule", BigDecimal.ONE, BigDecimal.TEN));

        assertThat(extractImps(result))
                .containsOnly(givenImp(imp -> imp
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(ext.set("prebid", extPrebid.set("floors", extPrebidFloors)))));
    }

    @Test
    public void shouldTolerateFloorResolvingError() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));

        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(givenModelGroup(identity()))))));

        final AuctionContext auctionContext = givenAuctionContext(
                givenAccount(identity()),
                givenBidRequest(
                        request -> request.imp(imps),
                        requestFloors));

        given(floorResolver.resolve(any(), any(), any(), any()))
                .willThrow(new IllegalStateException("error"));

        // when
        final AuctionContext result = priceFloorProcessor.enrichWithPriceFloors(auctionContext);

        // then
        assertThat(extractImps(result))
                .isEqualTo(imps);

        assertThat(result.getPrebidErrors())
                .containsOnly("Cannot resolve bid floor, error: error");
    }

    private static AuctionContext givenAuctionContext(Account account, BidRequest bidRequest) {
        return AuctionContext.builder()
                .prebidErrors(new ArrayList<>())
                .account(account)
                .bidRequest(bidRequest)
                .build();
    }

    private static Account givenAccount(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> floorsConfigCustomizer) {

        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(floorsConfigCustomizer.apply(AccountPriceFloorsConfig.builder()).build())
                        .build())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              PriceFloorRules floors) {

        return requestCustomizer.apply(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().floors(floors).build())))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(jacksonMapper.mapper().createObjectNode()))
                .build();
    }

    private static PriceFloorRules givenFloors(
            UnaryOperator<PriceFloorRules.PriceFloorRulesBuilder> floorsCustomizer) {

        return floorsCustomizer.apply(PriceFloorRules.builder()
                .data(PriceFloorData.builder()
                        .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                .value("someKey", BigDecimal.ONE)
                                .build()))
                        .build())
        ).build();
    }

    private static PriceFloorData givenFloorData(
            UnaryOperator<PriceFloorData.PriceFloorDataBuilder> floorDataCustomizer) {

        return floorDataCustomizer.apply(PriceFloorData.builder()
                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                        .value("someKey", BigDecimal.ONE)
                        .build()))).build();
    }

    private static PriceFloorModelGroup givenModelGroup(
            UnaryOperator<PriceFloorModelGroup.PriceFloorModelGroupBuilder> modelGroupCustomizer) {

        return modelGroupCustomizer.apply(PriceFloorModelGroup.builder()
                        .value("someKey", BigDecimal.ONE))
                .build();
    }

    private static PriceFloorRules extractFloors(AuctionContext auctionContext) {
        return auctionContext.getBidRequest().getExt().getPrebid().getFloors();
    }

    private static List<Imp> extractImps(AuctionContext auctionContext) {
        return auctionContext.getBidRequest().getImp();
    }
}
