package org.prebid.server.floors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.floors.model.PriceFloorField.siteDomain;
import static org.prebid.server.floors.model.PriceFloorField.size;

@ExtendWith(MockitoExtension.class)
public class BasicPriceFloorProcessorTest extends VertxTest {

    @Mock
    private PriceFloorFetcher priceFloorFetcher;
    @Mock
    private PriceFloorResolver floorResolver;
    @Mock
    private Metrics metrics;

    private BasicPriceFloorProcessor target;

    @BeforeEach
    public void setUp() {
        target = new BasicPriceFloorProcessor(priceFloorFetcher, floorResolver, metrics, jacksonMapper, 0.0d);
    }

    @Test
    public void shouldSetRulesEnabledFieldToFalseIfPriceFloorsDisabledForAccount() {
        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(floorsConfig -> floorsConfig.enabled(false)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        verifyNoInteractions(priceFloorFetcher, metrics);
        assertThat(result).isEqualTo(givenBidRequest(identity(), PriceFloorRules.builder().enabled(false).build()));
    }

    @Test
    public void shouldSetRulesEnabledFieldToFalseIfPriceFloorsDisabledForRequest() {
        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.enabled(false))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        verifyNoInteractions(priceFloorFetcher, metrics);

        assertThat(result)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getFloors)
                .extracting(PriceFloorRules::getEnabled)
                .isEqualTo(false);
    }

    @Test
    public void shouldUseFloorsDataFromProviderIfPresent() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .skipped(false)
                        .floorProvider("provider.com")
                        .floorMin(BigDecimal.ONE)
                        .data(providerFloorsData)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
        verifyNoInteractions(metrics);

    }

    @Test
    public void shouldUseFloorsFromProviderIfUseDynamicDataAndUseFetchDataRateAreAbsent() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors.useFetchDataRate(null));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(null)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .skipped(false)
                        .floorProvider("provider.com")
                        .data(providerFloorsData)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldUseFloorsFromProviderIfUseDynamicDataIsAbsentAndUseFetchDataRateIs100() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors.useFetchDataRate(100));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(null)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .enabled(true)
                .skipped(false)
                .floorProvider("provider.com")
                .data(providerFloorsData)
                .fetchStatus(FetchStatus.success)
                .location(PriceFloorLocation.fetch)));
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldNotUseFloorsFromProviderIfUseDynamicDataIsAbsentAndUseFetchDataRateIs0() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors.useFetchDataRate(0));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(null)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        final PriceFloorRules actualRules = extractFloors(result);
        assertThat(actualRules)
                .extracting(PriceFloorRules::getFetchStatus)
                .isEqualTo(FetchStatus.success);
        assertThat(actualRules)
                .extracting(PriceFloorRules::getLocation)
                .isEqualTo(PriceFloorLocation.noData);
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldTolerateInvalidFloorsFromRequestWhenFetchIsSuccessAndUseFetchDataRateIs0() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors
                .floorProvider("provider.com")
                .useFetchDataRate(0));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));
        final ArrayList<String> warnings = new ArrayList<>();

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.success)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());

        assertThat(warnings).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldUseFloorsFromProviderIfUseDynamicDataIsTrueAndUseFetchDataRateIsAbsent() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors.useFetchDataRate(null));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(true)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .skipped(false)
                        .floorProvider("provider.com")
                        .data(providerFloorsData)
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldNotUseFloorsFromProviderIfUseDynamicDataIsFalse() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(false)),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.success)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("Using dynamic data is not allowed for account accountId");
    }

    @Test
    public void shouldNotTolerateInvalidFloorsFromRequestWhenFetchIsSuccessAndUseDynamicDataIsFalse() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(floors -> floors
                .floorProvider("provider.com")
                .useFetchDataRate(0));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));
        final ArrayList<String> warnings = new ArrayList<>();

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(false)),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.success)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());

        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("Using dynamic data is not allowed for account accountId. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor rules data must be present");
    }

    @Test
    public void shouldUseFloorsFromRequestIfUseDynamicDataIsFalse() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(floorsConfig -> floorsConfig.useDynamicData(false)),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .enabled(true)
                .skipped(false)
                .fetchStatus(FetchStatus.success)
                .floorMin(BigDecimal.ONE)
                .location(PriceFloorLocation.request)));
        verifyNoInteractions(metrics);
        assertThat(warnings).isEmpty();
    }

    @Test
    public void shouldNotUseFloorsWhenProviderFetchingIsDisabled() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any()))
                .willReturn(FetchResult.of(providerFloorsData, FetchStatus.none, "errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage");
    }

    @Test
    public void shouldNotTolerateInvalidFloorsFromRequestWhenFetchIsDisabled() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor rules data must be present");
    }

    @Test
    public void shouldNotUseFloorsWhenProviderFetchingIsFailed() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.error("errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.error)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage");
    }

    @Test
    public void shouldNotTolerateInvalidFloorsFromRequestWhenFetchIsFailed() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.error("errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.error)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor rules data must be present");
    }

    @Test
    public void shouldNotUseFloorsWhenProviderFetchingIsFailedWithTimeout() {
        // given
        given(priceFloorFetcher.fetch(any()))
                .willReturn(FetchResult.of(null, FetchStatus.timeout, "errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.timeout)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage");
    }

    @Test
    public void shouldNotTolerateInvalidFloorsFromRequestWhenFetchIsFailedWithTimeout() {
        // given
        given(priceFloorFetcher.fetch(any()))
                .willReturn(FetchResult.of(null, FetchStatus.timeout, "errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        final PriceFloorRules actualRules = extractFloors(result);
        assertThat(actualRules)
                .extracting(PriceFloorRules::getFetchStatus)
                .isEqualTo(FetchStatus.timeout);
        assertThat(actualRules)
                .extracting(PriceFloorRules::getLocation)
                .isEqualTo(PriceFloorLocation.noData);
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(warnings).containsExactly("errorMessage. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor rules data must be present");
    }

    @Test
    public void shouldUseFloorsFromRequestIfFetchingIsDisabled() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .enabled(true)
                .skipped(false)
                .fetchStatus(FetchStatus.none)
                .floorMin(BigDecimal.ONE)
                .location(PriceFloorLocation.request)));
        verifyNoInteractions(metrics);
        assertThat(warnings).isEmpty();
    }

    @Test
    public void shouldUseFloorsFromRequestIfFetchingIsFailed() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.error("errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .enabled(true)
                .skipped(false)
                .fetchStatus(FetchStatus.error)
                .floorMin(BigDecimal.ONE)
                .location(PriceFloorLocation.request)));
        verifyNoInteractions(metrics);
        assertThat(warnings).isEmpty();
    }

    @Test
    public void shouldUseFloorsFromRequestIfFetchingIsFailedWithTimeout() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(null, FetchStatus.timeout, "errorMessage"));

        // when
        final ArrayList<String> warnings = new ArrayList<>();
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .enabled(true)
                .skipped(false)
                .fetchStatus(FetchStatus.timeout)
                .floorMin(BigDecimal.ONE)
                .location(PriceFloorLocation.request)));
        verifyNoInteractions(metrics);
        assertThat(warnings).isEmpty();
    }

    @Test
    public void shouldMergeProviderWithRequestFloors() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors
                                .enabled(true)
                                .enforcement(PriceFloorEnforcement.builder().enforcePbs(false).enforceRate(100).build())
                                .floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>()
        );

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                        .enabled(true)
                        .skipped(false)
                        .floorProvider("provider.com")
                        .enforcement(PriceFloorEnforcement.builder().enforcePbs(false).enforceRate(100).build())
                        .data(providerFloorsData)
                        .floorMin(BigDecimal.ONE)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch)));
    }

    @Test
    public void shouldReturnProviderFloorsWhenNotEnabledByRequestAndEnforceRateAndFloorPriceAreAbsent() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(
                        identity(),
                        givenFloors(floors -> floors.data(givenFloorData(identity())).enabled(null))),
                givenAccount(floorsConfig -> floorsConfig.enabled(true)),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        final PriceFloorRules expectedResult = givenFloors(floors -> floors
                        .enabled(true)
                        .skipped(false)
                        .floorProvider("provider.com")
                        .data(providerFloorsData)
                        .fetchStatus(FetchStatus.success)
                        .location(PriceFloorLocation.fetch));

        assertThat(extractFloors(result)).isEqualTo(expectedResult);
    }

    @Test
    public void shouldReturnFloorsWithFloorMinAndCurrencyFromRequestWhenPresent() {
        // given
        final PriceFloorRules givenFloors = givenFloors(floors -> floors
                .enabled(true)
                .floorMin(BigDecimal.ONE)
                .data(givenFloorData(floorsDataConfig -> floorsDataConfig.currency("USD"))));

        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getFloorMin, PriceFloorRules::getFloorMinCur)
                .containsExactly(BigDecimal.ONE, "USD");
    }

    @Test
    public void shouldUseFloorsFromRequestIfProviderFloorsMissing() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipped(false)
                .floorMin(BigDecimal.ONE)
                .location(PriceFloorLocation.request)));
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldTolerateUsingFloorsFromRequestWhenRulesNumberMoreThanMaxRulesNumber() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));
        final ArrayList<String> warnings = new ArrayList<>();

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(
                        PriceFloorData.builder()
                                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                        .values(Map.of("someKey", BigDecimal.ONE, "someKey2", BigDecimal.ONE))
                                        .schema(PriceFloorSchema.of("|", List.of(size, siteDomain)))
                                        .build()))
                                .build())
                )),
                givenAccount(floorConfigBuilder -> floorConfigBuilder.maxRules(1L)),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());

        assertThat(warnings).containsOnly("errorMessage. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor rules number 2 exceeded its maximum number 1");
    }

    @Test
    public void shouldTolerateUsingFloorsFromRequestWhenDimensionsNumberMoreThanMaxDimensionsNumber() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));
        final ArrayList<String> warnings = new ArrayList<>();

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(
                        PriceFloorData.builder()
                                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                        .value("someKey", BigDecimal.ONE)
                                        .schema(PriceFloorSchema.of("|", List.of(size, siteDomain)))
                                        .build()))
                                .build())
                )),
                givenAccount(floorConfigBuilder -> floorConfigBuilder.maxSchemaDims(1L)),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());

        assertThat(warnings).containsOnly("errorMessage. "
                + "Failed to parse price floors from request with id: 'request-id', with a reason: "
                + "Price floor schema dimensions 2 exceeded its maximum number 1");
    }

    @Test
    public void shouldTolerateInvalidFloorsFromRequestWhenFetchIsInProgress() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.inProgress());
        final ArrayList<String> warnings = new ArrayList<>();

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(null))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                warnings);

        // then
        assertThat(extractFloors(result)).isEqualTo(PriceFloorRules.builder()
                .fetchStatus(FetchStatus.inprogress)
                .enabled(true)
                .skipped(false)
                .location(PriceFloorLocation.noData)
                .build());

        assertThat(warnings).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    public void shouldTolerateMissingRequestAndProviderFloors() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), null),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        final PriceFloorRules actualFloorRules = extractFloors(result);
        assertThat(actualFloorRules)
                .extracting(PriceFloorRules::getEnabled)
                .isEqualTo(true);
        assertThat(actualFloorRules)
                .extracting(PriceFloorRules::getLocation)
                .isEqualTo(PriceFloorLocation.noData);
    }

    @Test
    public void shouldNotSkipFloorsIfRootSkipRateIsOff() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.skipRate(0))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result))
                .isEqualTo(givenFloors(floors -> floors
                        .fetchStatus(FetchStatus.none)
                        .enabled(true)
                        .skipped(false)
                        .skipRate(0)
                        .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldSkipFloorsIfRootSkipRateIsOn() {
        // given
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.skipRate(100))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .fetchStatus(FetchStatus.none)
                .skipRate(100)
                .enabled(true)
                .skipped(true)
                .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldSkipFloorsIfDataSkipRateIsOn() {
        // given
        final PriceFloorData priceFloorData = givenFloorData(floorData -> floorData.skipRate(100));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.skipRate(0).data(priceFloorData))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .fetchStatus(FetchStatus.none)
                .enabled(true)
                .skipRate(100)
                .floorProvider("provider.com")
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
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.data(priceFloorData))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result)).isEqualTo(givenFloors(floors -> floors
                .fetchStatus(FetchStatus.none)
                .data(priceFloorData)
                .skipRate(100)
                .floorProvider("provider.com")
                .enabled(true)
                .skipped(true)
                .location(PriceFloorLocation.request)));
    }

    @Test
    public void shouldNotUpdateImpsIfSelectedModelGroupIsMissing() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData.modelGroups(null))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(request -> request.imp(imps), requestFloors),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>()
        );

        // then
        assertThat(extractImps(result)).isSameAs(imps);
    }

    @Test
    public void shouldUseSelectedModelGroup() {
        // given
        final PriceFloorModelGroup modelGroup = givenModelGroup(identity());
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData.modelGroups(singletonList(modelGroup)))));
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));

        // when
        target.enrichWithPriceFloors(
                givenBidRequest(request -> request.imp(singletonList(givenImp(identity()))), requestFloors),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        final ArgumentCaptor<PriceFloorRules> captor = ArgumentCaptor.forClass(PriceFloorRules.class);
        verify(floorResolver).resolve(any(), captor.capture(), any(), eq("bidder"), any());
        assertThat(captor.getValue())
                .extracting(PriceFloorRules::getData)
                .extracting(PriceFloorData::getModelGroups)
                .isEqualTo(singletonList(modelGroup));
    }

    @Test
    public void shouldCopyFloorProviderValueFromDataLevel() {
        // given
        final PriceFloorData providerFloorsData = givenFloorData(identity());
        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.of(providerFloorsData, FetchStatus.success, null));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(identity(), givenFloors(floors -> floors.floorMin(BigDecimal.ONE))),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractFloors(result))
                .extracting(PriceFloorRules::getData)
                .extracting(PriceFloorData::getFloorProvider)
                .isEqualTo("provider.com");
    }

    @Test
    public void shouldNotUpdateImpsIfBidFloorNotResolved() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));

        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(givenModelGroup(identity()))))));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));
        given(floorResolver.resolve(any(), any(), any(), eq("bidder"), any())).willReturn(null);

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(request -> request.imp(imps), requestFloors),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        assertThat(extractImps(result)).isEqualTo(imps);
    }

    @Test
    public void shouldUpdateImpsIfBidFloorResolved() {
        // given
        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData
                        .modelGroups(singletonList(givenModelGroup(identity()))))));

        final JsonNode impFloorsNode = mapper.valueToTree(ExtImpPrebidFloors.of(
                null, null, null, BigDecimal.TEN, "CUR"));
        final ObjectNode givenImpExt = mapper.createObjectNode();
        final ObjectNode givenImpExtPrebid = mapper.createObjectNode();
        givenImpExtPrebid.set("floors", impFloorsNode);
        givenImpExt.set("prebid", givenImpExtPrebid);

        final List<Imp> imps = singletonList(givenImp(impBuilder -> impBuilder.ext(givenImpExt)));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));
        given(floorResolver.resolve(any(), any(), any(), eq("bidder"), any()))
                .willReturn(PriceFloorResult.of("rule", BigDecimal.ONE, BigDecimal.TEN, "USD"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(request -> request.imp(imps), requestFloors),
                givenAccount(identity()),
                "bidder",
                new ArrayList<>(),
                new ArrayList<>());

        // then
        final ObjectNode ext = jacksonMapper.mapper().createObjectNode();
        final ObjectNode extPrebid = jacksonMapper.mapper().createObjectNode();
        final ObjectNode extPrebidFloors = jacksonMapper.mapper().valueToTree(
                ExtImpPrebidFloors.of("rule", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, "CUR"));

        assertThat(extractImps(result)).containsOnly(givenImp(imp -> imp
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(ext.set("prebid", extPrebid.set("floors", extPrebidFloors)))));
    }

    @Test
    public void shouldTolerateFloorResolvingError() {
        // given
        final List<Imp> imps = singletonList(givenImp(identity()));
        final List<String> errors = new ArrayList<>();

        final PriceFloorRules requestFloors = givenFloors(floors -> floors
                .data(givenFloorData(floorData -> floorData.modelGroups(singletonList(givenModelGroup(identity()))))));

        given(priceFloorFetcher.fetch(any())).willReturn(FetchResult.none("errorMessage"));
        given(floorResolver.resolve(any(), any(), any(), eq("bidder"), any()))
                .willThrow(new IllegalStateException("error"));

        // when
        final BidRequest result = target.enrichWithPriceFloors(
                givenBidRequest(request -> request.imp(imps), requestFloors),
                givenAccount(identity()),
                "bidder",
                errors,
                new ArrayList<>());

        // then
        assertThat(extractImps(result)).isEqualTo(imps);
        assertThat(errors).containsOnly("Cannot resolve bid floor, error: error");
    }

    private static Account givenAccount(
            UnaryOperator<AccountPriceFloorsConfig.AccountPriceFloorsConfigBuilder> floorsConfigCustomizer) {

        return Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(floorsConfigCustomizer.apply(AccountPriceFloorsConfig.builder()).build())
                        .build())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              PriceFloorRules floors) {

        return requestCustomizer.apply(BidRequest.builder()
                        .id("request-id")
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
                                .schema(PriceFloorSchema.of("|", List.of(size)))
                                .build()))
                        .build())
        ).build();
    }

    private static PriceFloorData givenFloorData(
            UnaryOperator<PriceFloorData.PriceFloorDataBuilder> floorDataCustomizer) {

        return floorDataCustomizer.apply(PriceFloorData.builder()
                .floorProvider("provider.com")
                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                        .value("someKey", BigDecimal.ONE)
                        .schema(PriceFloorSchema.of("|", List.of(size)))
                        .build()))).build();
    }

    private static PriceFloorModelGroup givenModelGroup(
            UnaryOperator<PriceFloorModelGroup.PriceFloorModelGroupBuilder> modelGroupCustomizer) {

        return modelGroupCustomizer.apply(PriceFloorModelGroup.builder()
                        .value("someKey", BigDecimal.ONE)
                        .schema(PriceFloorSchema.of("|", List.of(size))))
                .build();
    }

    private static PriceFloorRules extractFloors(BidRequest bidRequest) {
        return bidRequest.getExt().getPrebid().getFloors();
    }

    private static List<Imp> extractImps(BidRequest bidRequest) {
        return bidRequest.getImp();
    }
}
