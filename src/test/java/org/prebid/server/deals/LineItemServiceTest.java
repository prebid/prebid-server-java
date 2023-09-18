package org.prebid.server.deals;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.deals.lineitem.DeliveryPlan;
import org.prebid.server.deals.lineitem.DeliveryToken;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.FrequencyCap;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.deals.targeting.TargetingDefinition;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LineItemServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TargetingService targetingService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private CurrencyConversionService conversionService;
    @Mock
    private ApplicationEventService applicationEventService;
    @Mock
    private Clock clock;
    @Mock
    private CriteriaLogManager criteriaLogManager;

    private BidderAliases bidderAliases;

    private LineItemService lineItemService;

    private ZonedDateTime now;

    @Before
    public void setUp() {
        now = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC));

        given(clock.instant()).willReturn(now.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        given(conversionService.convertCurrency(any(), anyMap(), anyString(), anyString(), any()))
                .willReturn(BigDecimal.ONE);

        bidderAliases = BidderAliases.of(Map.of("rubiAlias", "rubicon"), emptyMap(), bidderCatalog);

        lineItemService = new LineItemService(
                2,
                targetingService,
                conversionService,
                applicationEventService,
                "USD",
                clock,
                criteriaLogManager);
    }

    @Test
    public void updateLineItemsShouldRemoveLineItemIfItIsNotActiveFromPlannerResponse() {
        // given
        final List<LineItemMetaData> firstPlanResponse = asList(
                givenLineItemMetaData("lineItem1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()),
                givenLineItemMetaData("lineItem2", now, "2",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        final List<LineItemMetaData> secondPlanResponse = asList(
                givenLineItemMetaData("lineItem1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))),
                        builder -> builder.status("inactive")),
                givenLineItemMetaData("lineItem2", now, "2",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
        lineItemService.updateLineItems(secondPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
    }

    @Test
    public void updateLineItemsShouldRemoveLineItemIfItHasEndTimeInPastInPlannerResponse() {
        // given
        final List<LineItemMetaData> firstPlanResponse = asList(
                givenLineItemMetaData("lineItem1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()),
                givenLineItemMetaData("lineItem2", now, "2",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        final List<LineItemMetaData> secondPlanResponse = asList(
                givenLineItemMetaData("lineItem1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))),
                        builder -> builder.endTimeStamp(now.minusHours(1))),
                givenLineItemMetaData("lineItem2", now, "2",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
        lineItemService.updateLineItems(secondPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
    }

    @Test
    public void updateLineItemsShouldRemoveLineItemIfItHasEndTimeInPastInMemory() {
        // given
        final List<LineItemMetaData> firstPlanResponse = asList(
                givenLineItemMetaData("lineItem1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))),
                        builder -> builder.endTimeStamp(now.plusSeconds(1))),
                givenLineItemMetaData("lineItem2", now, "2",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true, now);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
        lineItemService.updateLineItems(emptyList(), true, now.plusSeconds(2));
        assertThat(lineItemService.getLineItemById("lineItem1")).isNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
    }

    @Test
    public void updateLineItemsShouldNotRemoveLineItemIfItWasMissedInPlannerResponse() {
        // given
        final List<LineItemMetaData> firstPlanResponse = asList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), now),
                givenLineItemMetaData("lineItem2", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), now));

        final List<LineItemMetaData> secondPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), now));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
        lineItemService.updateLineItems(secondPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
        assertThat(lineItemService.getLineItemById("lineItem2")).isNotNull();
    }

    @Test
    public void updateLineItemShouldSaveLineItemIfItHasEmptyDeliverySchedules() {
        // given
        final List<LineItemMetaData> firstPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon", emptyList(), now));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
    }

    @Test
    public void updateLineItemShouldSaveLineItemIfItDoesNotHaveDeliverySchedules() {
        // given
        final List<LineItemMetaData> firstPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon", null, now));

        // when and then
        lineItemService.updateLineItems(firstPlanResponse, true);
        assertThat(lineItemService.getLineItemById("lineItem1")).isNotNull();
    }

    @Test
    public void updateLineItemsShouldUpdateCurrentPlanIfUpdatedPlanUpdateTimeStampIsInFuture() {
        // given
        final List<LineItemMetaData> firstPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), now));

        final List<LineItemMetaData> secondPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now.plusMinutes(1), singleton(Token.of(1, 200)))), now));

        // when
        lineItemService.updateLineItems(firstPlanResponse, true);
        incSpentTokens(10, "lineItem1");
        lineItemService.updateLineItems(secondPlanResponse, true);

        // then
        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem).isNotNull();

        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();

        assertThat(activeDeliveryPlan).isNotNull()
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getStartTimeStamp, DeliveryPlan::getEndTimeStamp,
                        DeliveryPlan::getUpdatedTimeStamp)
                .containsOnly("planId1", now.minusHours(1), now.plusHours(1), now.plusMinutes(1));
        assertThat(activeDeliveryPlan.getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsOnly(tuple(1, 200, 10L));

        // 6 minutes after plan started
        assertThat(lineItem.getReadyAt()).isEqualTo(now.minusHours(1).plusMinutes(6));
    }

    @Test
    public void updateLineItemsShouldUpdateReadyAtBasedOnPlanStartTime() {
        // given
        final List<LineItemMetaData> firstPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), now, singleton(Token.of(1, 100)))), now));

        // when
        lineItemService.updateLineItems(firstPlanResponse, true);
        incSpentTokens(1, "lineItem1");

        // then
        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem).isNotNull();

        // 1 * (120  * 60 * 1000) \ 100 tokens = 72,000 millis = 1 minute 12 seconds shift from plan startTime
        assertThat(lineItem.getReadyAt()).isEqualTo(now.minusHours(1).plusMinutes(1).plusSeconds(12));
    }

    @Test
    public void updateLineItemsShouldConvertPriceWhenLineItemMetaDataCurrencyIsDifferent() {
        // given
        final String defaultCurrency = "RUB";
        lineItemService = new LineItemService(
                2,
                targetingService,
                conversionService,
                applicationEventService,
                defaultCurrency,
                clock,
                criteriaLogManager);

        final List<LineItemMetaData> planResponse = asList(
                givenLineItemMetaData("lineItem1", null, null,
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusHours(1),
                                emptySet())), now),
                givenLineItemMetaData("lineItem2", null, null,
                        singletonList(givenDeliverySchedule("planId2", now.minusHours(1), now.plusHours(1),
                                emptySet())), now));

        final BigDecimal updatedCmp = BigDecimal.TEN;
        given(conversionService.convertCurrency(any(), anyMap(), anyString(), anyString(), any()))
                .willReturn(updatedCmp);

        // when
        lineItemService.updateLineItems(planResponse, true);

        // then
        final LineItem lineItem1 = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem1.getCpm()).isEqualTo(updatedCmp);
        assertThat(lineItem1.getCurrency()).isEqualTo(defaultCurrency);

        final LineItem lineItem2 = lineItemService.getLineItemById("lineItem2");
        assertThat(lineItem2.getCpm()).isEqualTo(updatedCmp);
        assertThat(lineItem2.getCurrency()).isEqualTo(defaultCurrency);
    }

    @Test
    public void updateLineItemsShouldCreateLineItemsWhenPlannerIsResponsive() {
        // given
        final List<LineItemMetaData> planResponse = asList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), singleton(Token.of(1, 100)))), now),
                givenLineItemMetaData("lineItem2", "1002", "rubicon",
                        singletonList(givenDeliverySchedule("planId2", now.plusHours(1),
                                now.plusHours(2), singleton(Token.of(1, 100)))), now));

        // when
        lineItemService.updateLineItems(planResponse, true);

        // then
        final LineItem lineItem1 = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem1).isNotNull();

        final DeliveryPlan activeDeliveryPlan = lineItem1.getActiveDeliveryPlan();
        assertThat(activeDeliveryPlan).isNotNull()
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getStartTimeStamp, DeliveryPlan::getEndTimeStamp)
                .containsOnly("planId1", now.minusHours(1), now.plusHours(1));
        assertThat(activeDeliveryPlan.getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal)
                .containsOnly(tuple(1, 100));

        assertThat(lineItem1.getReadyAt()).isEqualTo(now);

        final LineItem lineItem2 = lineItemService.getLineItemById("lineItem2");
        assertThat(lineItem2).isNotNull();
        assertThat(lineItem2.getActiveDeliveryPlan()).isNull();
        assertThat(lineItem2.getReadyAt()).isNull();
    }

    @Test
    public void updateLineItemsShouldCreateLineItemsWithNullTargetingIfCantParse() {
        // given
        final List<LineItemMetaData> planResponse = singletonList(
                LineItemMetaData.builder()
                        .lineItemId("lineItem1")
                        .status("active")
                        .dealId("dealId")
                        .accountId("1001")
                        .source("rubicon")
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .relativePriority(5)
                        .startTimeStamp(now.minusHours(1))
                        .endTimeStamp(now.plusHours(1))
                        .updatedTimeStamp(now)
                        .targeting(mapper.createObjectNode().set("$invalid", new TextNode("invalid")))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), singleton(Token.of(1, 100)))))
                        .build());

        // when
        lineItemService.updateLineItems(planResponse, true);

        // then
        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem).isNotNull();
        assertThat(lineItem.getTargetingDefinition()).isNull();
    }

    @Test
    public void updateLineItemsShouldNotUpdateCurrentPlanIfUpdatedPlanUpdateTimeStampIsNotInFuture() {
        // given
        final List<LineItemMetaData> firstPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), singleton(Token.of(1, 100)))), now));

        final List<LineItemMetaData> secondPlanResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusHours(1), singleton(Token.of(1, 200)))), now));

        // when
        lineItemService.updateLineItems(firstPlanResponse, true);
        incSpentTokens(10, "lineItem1");
        lineItemService.updateLineItems(secondPlanResponse, true);

        // then
        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");
        assertThat(lineItem).isNotNull();

        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();
        assertThat(activeDeliveryPlan).isNotNull()
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getStartTimeStamp, DeliveryPlan::getEndTimeStamp)
                .containsOnly("planId1", now.minusHours(1), now.plusHours(1));
        assertThat(activeDeliveryPlan.getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsOnly(tuple(1, 100, 10L));

        // should be ready at 12 minutes after plan start
        assertThat(lineItem.getReadyAt()).isEqualTo(now.minusHours(1).plusMinutes(12));
    }

    @Test
    public void updateLineItemsShouldMergeLineItemsWhenPlannerIsNotResponsive() {
        // given
        given(clock.instant()).willReturn(now.toInstant(), now.toInstant(), now.plusSeconds(2).toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        final Set<Token> expiredTokens = new HashSet<>();
        expiredTokens.add(Token.of(1, 100));
        expiredTokens.add(Token.of(3, 300));
        expiredTokens.add(Token.of(4, 400));
        expiredTokens.add(Token.of(5, 500));

        final Set<Token> newActiveTokens = new HashSet<>();
        newActiveTokens.add(Token.of(1, 100));
        newActiveTokens.add(Token.of(2, 200));
        newActiveTokens.add(Token.of(3, 300));
        newActiveTokens.add(Token.of(4, 400));

        final List<LineItemMetaData> planResponse = singletonList(givenLineItemMetaData(
                "lineItem1", "1001", "rubicon",
                asList(
                        givenDeliverySchedule("planId1", now.minusHours(1), now.plusSeconds(1), expiredTokens),
                        givenDeliverySchedule("planId2", now.plusSeconds(1), now.plusHours(1), newActiveTokens)),
                now));

        // when and then
        lineItemService.updateLineItems(planResponse, true);
        incSpentTokens(240, "lineItem1");
        lineItemService.updateLineItems(null, false);
        lineItemService.advanceToNextPlan(now.plusSeconds(1));

        verify(clock, times(2)).instant();

        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");

        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();
        assertThat(activeDeliveryPlan).isNotNull();
        assertThat(activeDeliveryPlan.getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal,
                        deliveryToken -> deliveryToken.getSpent().sum())
                .containsExactly(
                        tuple(1, 200, 100L),
                        tuple(2, 200, 0L),
                        tuple(3, 600, 140L),
                        tuple(4, 800, 0L),
                        tuple(5, 500, 0L));
    }

    @Test
    public void updateLineItemShouldUpdatePlanWithoutActiveCurrentDeliveryPlan() {
        // given
        given(clock.instant()).willReturn(now.toInstant(), now.toInstant(), now.plusSeconds(3).toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        final List<LineItemMetaData> planResponse = singletonList(givenLineItemMetaData(
                "lineItem1", "1001", "rubicon",
                asList(
                        givenDeliverySchedule("planId1", now.minusHours(2),
                                now.minusHours(1), singleton(Token.of(1, 100))),
                        givenDeliverySchedule("planId2", now.plusSeconds(2),
                                now.plusHours(1), singleton(Token.of(1, 100)))),
                now));

        // when and then
        lineItemService.updateLineItems(planResponse, true);
        lineItemService.updateLineItems(null, false);
        lineItemService.advanceToNextPlan(now.plusSeconds(2));

        verify(clock, times(2)).instant();

        final LineItem lineItem = lineItemService.getLineItemById("lineItem1");

        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();
        assertThat(activeDeliveryPlan).isNotNull();
        assertThat(activeDeliveryPlan.getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal,
                        deliveryToken -> deliveryToken.getSpent().sum())
                .containsExactly(tuple(1, 100, 0L));
    }

    @Test
    public void accountHasDealsShouldReturnTrue() {
        // given
        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusHours(1),
                                emptySet())), now));
        lineItemService.updateLineItems(planResponse, true);

        // when and then
        assertThat(lineItemService.accountHasDeals(AuctionContext.builder()
                .account(Account.builder().id("1001").build()).build()))
                .isTrue();
    }

    @Test
    public void accountHasDealsShouldReturnFalseWhenAccountIsEmptyString() {
        // given
        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusHours(1),
                                emptySet())), now));
        lineItemService.updateLineItems(planResponse, true);

        // when and then
        assertThat(lineItemService.accountHasDeals(AuctionContext.builder().account(Account.builder().id("").build())
                .build())).isFalse();
    }

    @Test
    public void accountHasDealsShouldReturnFalseWhenNoMatchingLineItemWereFound() {
        // given
        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "3003", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusHours(1),
                                emptySet())), now));
        lineItemService.updateLineItems(planResponse, true);

        // when and then
        assertThat(lineItemService.accountHasDeals(AuctionContext.builder().account(Account.builder().id("1001")
                .build()).build())).isFalse();
    }

    @Test
    public void accountHasDealsShouldReturnFalseWhenMatchedLineItemIsNotActive() {
        // given
        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusHours(1),
                                emptySet())), now));
        lineItemService.updateLineItems(planResponse, true);
        given(clock.instant()).willReturn(now.plusHours(2).toInstant());

        // when and then
        assertThat(lineItemService.accountHasDeals(AuctionContext.builder().account(Account.builder().id("1001")
                .build()).build())).isFalse();
    }

    @Test
    public void findMatchingLineItemsShouldReturnEmptyListWhenLineItemsIsEmpty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldReturnEmptyListWhenNoLineItemsForAccount() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountIdUnknown", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldReturnEmptyListWhenNoBiddersMatched() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountId", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusMinutes(1),
                                singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "pubmatic", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldReturnLineItemsForMatchingBidder() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountId", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusMinutes(1),
                                singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("lineItem1");
    }

    @Test
    public void findMatchingLineItemsShouldReturnLineItemsWhenLineItemsBidderIsAlias() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountId", "rubiAlias",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusMinutes(1),
                                singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("lineItem1");
    }

    @Test
    public void findMatchingLineItemsShouldReturnLineItemsWhenBidderFromInputBiddersIsAlias() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountId", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusMinutes(1),
                                singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubiAlias", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("lineItem1");
    }

    @Test
    public void findMatchingLineItemsShouldReturnEmptyListWhenAccountIsEmptyString() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder().account(Account.builder().id("").build()).build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = singletonList(
                givenLineItemMetaData("lineItem1", "accountId", "rubicon",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1), now.plusMinutes(1),
                                singleton(Token.of(1, 100)))), now));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldFilterNotMatchingTargeting() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        given(targetingService.parseTargetingDefinition(any(), eq("id1")))
                .willReturn(TargetingDefinition.of(context -> false));
        given(targetingService.parseTargetingDefinition(any(), eq("id2")))
                .willReturn(TargetingDefinition.of(context -> true));
        given(targetingService.matchesTargeting(any(), any(), any(), any()))
                .willAnswer(withEvaluatedTargeting());

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).isEmpty();

        assertThat(auctionContext.getDeepDebugLog().entries()).containsOnly(
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.targeting,
                        "Line Item id1 targeting did not match imp with id imp1"));
    }

    @Test
    public void findMatchingLineItemsShouldReturnLineItemsThatMatchedTargeting() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        given(targetingService.parseTargetingDefinition(any(), eq("id1")))
                .willReturn(TargetingDefinition.of(context -> false));
        given(targetingService.parseTargetingDefinition(any(), eq("id2")))
                .willReturn(TargetingDefinition.of(context -> true));
        given(targetingService.matchesTargeting(any(), any(), any(), any()))
                .willAnswer(withEvaluatedTargeting());

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "appnexus", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");

        assertThat(auctionContext.getDeepDebugLog().entries()).containsOnly(
                ExtTraceDeal.of("id2", ZonedDateTime.now(clock), Category.targeting,
                        "Line Item id2 targeting matched imp with id imp1"),
                ExtTraceDeal.of("id2", ZonedDateTime.now(clock), Category.pacing,
                        "Matched Line Item id2 for bidder appnexus ready to serve. relPriority null"));
    }

    @Test
    public void findMatchingLineItemsShouldFilterNullTargeting() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        given(targetingService.parseTargetingDefinition(any(), eq("id1")))
                .willReturn(null);
        given(targetingService.parseTargetingDefinition(any(), eq("id2")))
                .willReturn(TargetingDefinition.of(context -> true));
        given(targetingService.matchesTargeting(any(), any(), any(), any()))
                .willAnswer(withEvaluatedTargeting());

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).isEmpty();

        assertThat(auctionContext.getDeepDebugLog().entries()).containsOnly(
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.targeting,
                        "Line Item id1 targeting was not defined or has incorrect format"));
    }

    @Test
    public void findMatchingLineItemsShouldFilterNotReadyLineItems() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusMinutes(1),
                                now.plusDays(1), singleton(Token.of(1, 1)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);
        lineItemService.getLineItemById("id1").incSpentToken(now.plusSeconds(1));

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldFilterLineItemsWithFcapIdsWhenUserDetailsFcapIsNull() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(null);

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .frequencyCaps(singletonList(FrequencyCap.builder().fcapId("123").build()))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).isEmpty();
    }

    @Test
    public void findMatchingLineItemsShouldFilterFcappedLineItems() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(asList("fcap2", "fcap3"));

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .frequencyCaps(singletonList(FrequencyCap.builder().fcapId("fcap2").build()))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).isEmpty();
        assertThat(auctionContext.getTxnLog().lineItemsMatchedTargetingFcapped()).containsOnly("id1");
        assertThat(auctionContext.getDeepDebugLog().entries()).contains(
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.pacing,
                        "Matched Line Item id1 for bidder rubicon is frequency capped by fcap id fcap2."));
    }

    @Test
    public void findMatchingLineItemsShouldFilterSameSentToBidderAsTopMatchLineItemsPerBidder() {
        // given
        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemsSentToBidderAsTopMatch().put("rubicon", new HashSet<>(singleton("id1")));
        final AuctionContext auctionContext = givenAuctionContext(emptyList()).toBuilder().txnLog(txnLog).build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(2)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
    }

    @Test
    public void findMatchingLineItemsShouldFilterSameSentToBidderAsTopMatchLineItemsPerAllBidders() {
        // given
        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemsSentToBidderAsTopMatch().put("appnexus", new HashSet<>(singleton("id1")));
        final AuctionContext auctionContext = givenAuctionContext(emptyList()).toBuilder().txnLog(txnLog).build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .relativePriority(1)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(2)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
    }

    @Test
    public void findMatchingLineItemsShouldFilterLineItemsWithSameDealAndLowestPriorityTokenClassWithinBidder() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(3, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
    }

    @Test
    public void findMatchingLineItemsShouldFilterLineItemsWithSameDealIdAndLowestLineItemPriority() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(3)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
    }

    @Test
    public void findMatchingLineItemsShouldFilterLineItemsWithSameDealIdAndLowestCpm() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
    }

    @Test
    public void findMatchingLineItemsShouldFilterLineItemsWithoutUnspentTokensAndIncrementDeferred() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 0)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(auctionContext.getTxnLog().lineItemsPacingDeferred()).contains("id1");
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2");
        assertThat(auctionContext.getDeepDebugLog().entries()).contains(
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.pacing,
                        "Matched Line Item id1 for bidder rubicon does not have unspent tokens to be served"));
    }

    @Test
    public void findMatchingLineItemsShouldLimitLineItemsPerBidder() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id3")
                        .status("active")
                        .dealId("3")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id2", "id3");
    }

    @Test
    public void findMatchingLineItemsShouldReturnLineItemWithReadyToServeEqualToNow() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();
        givenClock(now, now.plusSeconds((now.plusMinutes(5).toEpochSecond() - now.toEpochSecond()) / 100));

        final List<LineItemMetaData> planResponse = singletonList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("futurePlanId", now.minusMinutes(1),
                                now.plusMinutes(5), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsOnly("id1");
    }

    @Test
    public void findMatchingLineItemsShouldRecordLineItemsInTxnLog() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(asList("fcap2", "fcap3"));

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .frequencyCaps(singletonList(FrequencyCap.builder().fcapId("fcap3").build()))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id3")
                        .status("active")
                        .dealId("3")
                        .source("appnexus")
                        .accountId("accountId")
                        .relativePriority(2)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id4")
                        .status("active")
                        .dealId("4")
                        .source("appnexus")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "appnexus", bidderAliases, auctionContext);

        // then
        final TxnLog expectedTxnLog = TxnLog.create();
        expectedTxnLog.lineItemsMatchedWholeTargeting().addAll(asList("id1", "id2", "id3", "id4"));
        expectedTxnLog.lineItemsMatchedTargetingFcapped().add("id2");
        expectedTxnLog.lineItemsReadyToServe().addAll(asList("id1", "id3", "id4"));
        expectedTxnLog.lineItemsSentToBidderAsTopMatch().put("rubicon", singleton("id1"));
        expectedTxnLog.lineItemsSentToBidderAsTopMatch().put("appnexus", singleton("id4"));
        expectedTxnLog.lineItemsSentToBidder().get("rubicon").add("id1");
        expectedTxnLog.lineItemsSentToBidder().get("appnexus").addAll(asList("id3", "id4"));
        expectedTxnLog.lostMatchingToLineItems().put("id3", singleton("id4"));
        assertThat(auctionContext.getTxnLog()).isEqualTo(expectedTxnLog);
    }

    @Test
    public void findMatchingLineItemsShouldRecordLineItemsInTxnLogWhenPacingDeferred() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());
        givenTargetingService();

        final List<LineItemMetaData> planResponse = singletonList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id5")
                        .status("active")
                        .dealId("5")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(2), singleton(Token.of(1, 2)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);
        lineItemService.getLineItemById("id5").incSpentToken(now.plusSeconds(1));
        lineItemService.getLineItemById("id5").incSpentToken(now.plusSeconds(1));

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        final TxnLog expectedTxnLog = TxnLog.create();
        expectedTxnLog.lineItemsMatchedWholeTargeting().add("id5");
        expectedTxnLog.lineItemsPacingDeferred().add("id5");
        assertThat(auctionContext.getTxnLog()).isEqualTo(expectedTxnLog);
    }

    @Test
    public void findMatchingLineItemsShouldRecordLineItemsInTxnLogWhenFcapLookupFailed() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(null);

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .frequencyCaps(singletonList(FrequencyCap.builder().fcapId("fcap3").build()))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(auctionContext.getTxnLog().lineItemsMatchedTargetingFcapLookupFailed()).containsOnly("id2");
        assertThat(auctionContext.getDeepDebugLog().entries()).contains(
                ExtTraceDeal.of("id2", ZonedDateTime.now(clock), Category.pacing,
                        "Failed to match fcap for Line Item id2 bidder rubicon in a reason of bad response"
                                + " from user data service"));
    }

    @Test
    public void findMatchingLineItemsShouldHasCorrectRandomDistribution() {
        // given
        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        lineItemService = new LineItemService(
                3,
                targetingService,
                conversionService,
                applicationEventService,
                "USD",
                clock,
                criteriaLogManager);

        final List<LineItemMetaData> planResponse = asList(
                givenLineItemMetaData("id1", now, "1",
                        singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), now, singleton(Token.of(1, 100)))), Function.identity()),
                givenLineItemMetaData("id2", now, "2",
                        singletonList(givenDeliverySchedule("planId2", now.minusHours(1),
                                now.plusMinutes(1), now, singleton(Token.of(1, 100)))), Function.identity()),
                givenLineItemMetaData("id3", now, "3",
                        singletonList(givenDeliverySchedule("planId3", now.minusHours(1),
                                now.plusMinutes(1), now, singleton(Token.of(1, 100)))), Function.identity()));

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            final AuctionContext auctionContext = givenAuctionContext(emptyList());
            final MatchLineItemsResult matchLineItemsResult = lineItemService.findMatchingLineItems(
                    auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);
            count.compute(matchLineItemsResult.getLineItems().get(0).getLineItemId(),
                    (s, integer) -> integer != null ? ++integer : 1);
        }

        // then
        assertThat(count.get("id1")).isBetween(290, 390);
        assertThat(count.get("id2")).isBetween(290, 390);
        assertThat(count.get("id3")).isBetween(290, 390);
    }

    @Test
    public void findMatchingLineItemsShouldAddDebugMessages() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList());

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id1")
                        .status("active")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(2)
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(1), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId("id2")
                        .status("active")
                        .source("appnexus")
                        .accountId("accountId")
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusMinutes(1),
                                now.plusDays(1), singleton(Token.of(1, 2)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);
        lineItemService.getLineItemById("id2").incSpentToken(now.plusSeconds(1));

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);
        lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "appnexus", bidderAliases, auctionContext);

        // then
        assertThat(auctionContext.getDeepDebugLog().entries()).containsOnly(
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.targeting,
                        "Line Item id1 targeting matched imp with id imp1"),
                ExtTraceDeal.of("id2", ZonedDateTime.now(clock), Category.targeting,
                        "Line Item id2 targeting matched imp with id imp1"),
                ExtTraceDeal.of("id1", ZonedDateTime.now(clock), Category.pacing,
                        "Matched Line Item id1 for bidder rubicon ready to serve. relPriority 2"),
                ExtTraceDeal.of("id2", ZonedDateTime.now(clock), Category.pacing,
                        "Matched Line Item id2 for bidder appnexus not ready to serve. Will be ready"
                                + " at 2019-07-26T21:59:30.000Z, current time is 2019-07-26T10:01:00.000Z"));
    }

    @Test
    public void findMatchingLineItemsShouldReturnWithoutUnspentTokensOrOnCoolDownIfIgnorePacingHeaderProvided() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder().add(HttpUtil.PG_IGNORE_PACING, "1").build())
                        .build())
                .build();

        givenTargetingService();

        givenClock(now.plusMinutes(5), now);

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 0)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsExactly("id2", "id1");
    }

    @Test
    public void findMatchingLineItemsShouldLowerPriorityIfDeliveryPlanIsNullAndPgIgnorePacing() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder().add(HttpUtil.PG_IGNORE_PACING, "1").build())
                        .build())
                .build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(null)
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsExactly("id1", "id2");
    }

    @Test
    public void findMatchingLineItemsShouldLowerPriorityIfNoTokensUnspentAndPgIgnorePacing() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder().add(HttpUtil.PG_IGNORE_PACING, "1").build())
                        .build())
                .build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 0)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsExactly("id1", "id2");
    }

    @Test
    public void findMatchingLineItemsShouldLowerPriorityIfRelativePriorityIsNullAndPgIgnorePacing() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder().add(HttpUtil.PG_IGNORE_PACING, "1").build())
                        .build())
                .build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(null)
                        .price(Price.of(BigDecimal.TEN, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsExactly("id1", "id2");
    }

    @Test
    public void findMatchingLineItemsShouldLowerPriorityIfCpmIsNullAndPgIgnorePacing() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(emptyList())
                .toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder().add(HttpUtil.PG_IGNORE_PACING, "1").build())
                        .build())
                .build();

        givenTargetingService();

        givenClock(now, now.plusMinutes(1));

        final List<LineItemMetaData> planResponse = asList(
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id1")
                        .status("active")
                        .dealId("dealId1")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(BigDecimal.ZERO, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build(),
                LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(10))
                        .lineItemId("id2")
                        .status("active")
                        .dealId("dealId2")
                        .source("rubicon")
                        .accountId("accountId")
                        .relativePriority(1)
                        .price(Price.of(null, "USD"))
                        .deliverySchedules(singletonList(givenDeliverySchedule("planId1", now.minusHours(1),
                                now.plusMinutes(10), singleton(Token.of(1, 100)))))
                        .build());

        lineItemService.updateLineItems(planResponse, true);

        final Imp imp = Imp.builder().id("imp1").build();

        // when
        final MatchLineItemsResult result = lineItemService.findMatchingLineItems(
                auctionContext.getBidRequest(), imp, "rubicon", bidderAliases, auctionContext);

        // then
        assertThat(result.getLineItems()).extracting(LineItem::getLineItemId).containsExactly("id1", "id2");
    }

    private static LineItemMetaData givenLineItemMetaData(
            String lineItemId,
            ZonedDateTime now,
            String dealId,
            List<DeliverySchedule> deliverySchedules,
            Function<LineItemMetaData.LineItemMetaDataBuilder,
                    LineItemMetaData.LineItemMetaDataBuilder> lineItemMetaDataCustomizer) {
        return lineItemMetaDataCustomizer.apply(LineItemMetaData.builder()
                        .startTimeStamp(now.minusMinutes(1))
                        .endTimeStamp(now.plusMinutes(1))
                        .lineItemId(lineItemId)
                        .dealId(dealId)
                        .status("active")
                        .accountId("accountId")
                        .source("rubicon")
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .relativePriority(5)
                        .updatedTimeStamp(now)
                        .deliverySchedules(deliverySchedules))
                .build();
    }

    private static LineItemMetaData givenLineItemMetaData(
            String lineItemId, String account, String bidderCode, List<DeliverySchedule> deliverySchedules,
            ZonedDateTime now) {

        return LineItemMetaData.builder()
                .startTimeStamp(now.minusMinutes(1))
                .endTimeStamp(now.plusMinutes(1))
                .lineItemId(lineItemId)
                .dealId("dealId")
                .status("active")
                .accountId(account)
                .source(bidderCode)
                .price(Price.of(BigDecimal.ONE, "USD"))
                .relativePriority(5)
                .startTimeStamp(now.minusHours(1))
                .endTimeStamp(now.plusHours(1))
                .updatedTimeStamp(now)
                .deliverySchedules(deliverySchedules)
                .build();
    }

    private static DeliverySchedule givenDeliverySchedule(String planId, ZonedDateTime start, ZonedDateTime end,
                                                          ZonedDateTime updated, Set<Token> tokens) {
        return DeliverySchedule.builder()
                .planId(planId)
                .startTimeStamp(start)
                .endTimeStamp(end)
                .updatedTimeStamp(updated)
                .tokens(tokens)
                .build();
    }

    private static DeliverySchedule givenDeliverySchedule(String planId, ZonedDateTime start, ZonedDateTime end,
                                                          Set<Token> tokens) {
        return givenDeliverySchedule(planId, start, end, start, tokens);
    }

    private void incSpentTokens(int times, String... lineItemIds) {
        for (final String lineItemId : lineItemIds) {
            final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
            IntStream.range(0, times).forEach(i -> lineItem.incSpentToken(now));
        }
    }

    private AuctionContext givenAuctionContext(List<String> fcaps) {
        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().headers(CaseInsensitiveMultiMap.empty()).build())
                .account(Account.builder().id("accountId").build())
                .deepDebugLog(DeepDebugLog.create(true, clock))
                .txnLog(TxnLog.create())
                .bidRequest(BidRequest.builder()
                        .user(User.builder().ext(
                                ExtUser.builder().fcapIds(fcaps).build()).build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("rubiAlias", "rubicon"))
                                .build()))
                        .build())
                .build();
    }

    private void givenTargetingService() {
        given(targetingService.parseTargetingDefinition(any(), any()))
                .willReturn(TargetingDefinition.of(context -> true));
        given(targetingService.matchesTargeting(any(), any(), any(), any()))
                .willAnswer(withEvaluatedTargeting());
    }

    private Answer<Boolean> withEvaluatedTargeting() {
        return invocation -> ((TargetingDefinition) invocation.getArgument(2)).getRootExpression().matches(null);
    }

    private void givenClock(ZonedDateTime... dateTimes) {
        given(clock.instant()).willReturn(
                dateTimes[0].toInstant(),
                Arrays.stream(dateTimes).skip(1).map(ZonedDateTime::toInstant).toArray(Instant[]::new));
        given(clock.getZone()).willReturn(dateTimes[0].getZone());
    }
}
