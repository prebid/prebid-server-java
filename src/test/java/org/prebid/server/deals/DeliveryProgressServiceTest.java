package org.prebid.server.deals;

import org.assertj.core.api.iterable.ThrowingExtractor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.lineitem.DeliveryPlan;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.lineitem.DeliveryToken;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.lineitem.LineItemStatus;
import org.prebid.server.deals.lineitem.LostToLineItem;
import org.prebid.server.deals.model.DeliveryProgressProperties;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.deals.proto.report.Event;
import org.prebid.server.deals.proto.report.LineItemStatusReport;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class DeliveryProgressServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LineItemService lineItemService;
    @Mock
    private DeliveryStatsService deliveryStatsService;
    @Mock
    private DeliveryProgressReportFactory deliveryProgressReportFactory;
    @Mock
    private CriteriaLogManager criteriaLogManager;

    private DeliveryProgressService deliveryProgressService;

    private final Clock clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC);
    private ZonedDateTime now;

    @Before
    public void setUp() {
        now = ZonedDateTime.now(clock);

        deliveryProgressService = new DeliveryProgressService(
                DeliveryProgressProperties.of(200L, 20),
                lineItemService,
                deliveryStatsService,
                deliveryProgressReportFactory,
                clock,
                criteriaLogManager);
    }

    @Test
    public void updateLineItemsShouldUpdateCurrentDeliveryReportIfUpdatedPlanUpdateTimeStampIsInFuture() {
        // given
        final LineItemMetaData firstPlanResponse = givenLineItemMetaData(
                now,
                lineItemMetaData -> lineItemMetaData
                        .lineItemId("lineItem1")
                        .accountId("1001")
                        .source("rubicon")
                        .deliverySchedules(singletonList(
                                givenDeliverySchedule(
                                        "planId1",
                                        now.minusHours(1),
                                        now.plusHours(1),
                                        now,
                                        singleton(Token.of(1, 100))))));
        final LineItemMetaData secondPlanResponse = givenLineItemMetaData(
                now,
                lineItemMetaData -> lineItemMetaData
                        .lineItemId("lineItem1")
                        .accountId("1001")
                        .source("rubicon")
                        .deliverySchedules(singletonList(
                                givenDeliverySchedule(
                                        "planId1",
                                        now.minusHours(1),
                                        now.plusHours(1),
                                        now.plusMinutes(1),
                                        singleton(Token.of(1, 200))))));

        final LineItem lineItem1 = LineItem.of(firstPlanResponse, null, null, now);
        final LineItem lineItem12 = LineItem.of(secondPlanResponse, null, null, now);

        given(lineItemService.getLineItems()).willReturn(
                singletonList(lineItem1),
                singletonList(lineItem12));

        given(lineItemService.getLineItemById(anyString())).willReturn(
                lineItem12);

        // when
        deliveryProgressService.processDeliveryProgressUpdateEvent();
        recordLineItemsServed(40, "lineItem1");
        deliveryProgressService.processDeliveryProgressUpdateEvent();

        // then
        // trigger overall progress passing to report factory
        deliveryProgressService.getOverallDeliveryProgressReport();

        final ArgumentCaptor<DeliveryProgress> overallProgressCaptor = ArgumentCaptor.forClass(DeliveryProgress.class);
        verify(deliveryProgressReportFactory).fromDeliveryProgress(overallProgressCaptor.capture(), any(),
                anyBoolean());

        final DeliveryProgress overallProgress = overallProgressCaptor.getValue();
        assertThat(overallProgress).isNotNull();
        assertThat(overallProgress.getLineItemStatuses()).isNotNull();
        assertThat(overallProgress.getLineItemStatuses().keySet())
                .containsOnly("lineItem1");
        final LineItemStatus overallLineItemStatus = overallProgress.getLineItemStatuses().get("lineItem1");
        assertThat(overallLineItemStatus).isNotNull();
        assertThat(overallLineItemStatus.getDeliveryPlans())
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getStartTimeStamp, DeliveryPlan::getEndTimeStamp,
                        DeliveryPlan::getUpdatedTimeStamp)
                .containsOnly(tuple("planId1", now.minusHours(1), now.plusHours(1), now.plusMinutes(1)));
        assertThat(overallLineItemStatus.getDeliveryPlans())
                .flatExtracting(DeliveryPlan::getDeliveryTokens)
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsOnly(tuple(1, 200, 40L));

        // trigger current progress passing to delivery stats
        deliveryProgressService.shutdown();

        final ArgumentCaptor<DeliveryProgress> currentProgressCaptor = ArgumentCaptor.forClass(DeliveryProgress
                .class);
        verify(deliveryStatsService).addDeliveryProgress(currentProgressCaptor.capture(), any());

        final DeliveryProgress currentProgress = currentProgressCaptor.getValue();
        assertThat(currentProgress).isNotNull();
        assertThat(currentProgress.getLineItemStatuses()).isNotNull();
        final LineItemStatus currentLineItemStatus = currentProgress.getLineItemStatuses().get("lineItem1");
        assertThat(currentLineItemStatus).isNotNull();
        assertThat(currentLineItemStatus.getDeliveryPlans())
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getStartTimeStamp, DeliveryPlan::getEndTimeStamp,
                        DeliveryPlan::getUpdatedTimeStamp)
                .containsOnly(tuple("planId1", now.minusHours(1), now.plusHours(1), now.plusMinutes(1)));
        assertThat(currentLineItemStatus.getDeliveryPlans())
                .flatExtracting(DeliveryPlan::getDeliveryTokens)
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsOnly(tuple(1, 200, 40L));
    }

    @Test
    public void processAuctionEventShouldUpdateCurrentPlan() {
        // given
        final String lineItemId1 = "lineItemId1";
        final String lineItemId2 = "lineItemId2";

        final LineItem lineItem1 = LineItem.of(
                givenLineItemMetaData(
                        now,
                        lineItemMetaData -> lineItemMetaData
                                .lineItemId(lineItemId1)
                                .accountId("1001")
                                .source("rubicon")
                                .deliverySchedules(singletonList(
                                        givenDeliverySchedule(
                                                "plan1",
                                                now.minusHours(1),
                                                now.plusHours(1),
                                                Set.of(Token.of(1, 100), Token.of(2, 100)))))),
                null,
                null,
                now);
        final LineItem lineItem2 = LineItem.of(
                givenLineItemMetaData(
                        now,
                        lineItemMetaData -> lineItemMetaData
                                .lineItemId(lineItemId2)
                                .accountId("1001")
                                .source("rubicon")
                                .deliverySchedules(singletonList(
                                        givenDeliverySchedule(
                                                "plan2",
                                                now.minusHours(1),
                                                now.plusHours(1),
                                                Set.of(Token.of(1, 100), Token.of(2, 100)))))),
                null,
                null,
                now);

        given(lineItemService.getLineItemById(eq(lineItemId1))).willReturn(lineItem1);
        given(lineItemService.getLineItemById(eq(lineItemId2))).willReturn(lineItem2);

        recordLineItemsServed(150, lineItemId1);

        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemSentToClientAsTopMatch().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsSentToClient().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsMatchedDomainTargeting().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsMatchedWholeTargeting().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsMatchedTargetingFcapped().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsMatchedTargetingFcapLookupFailed().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lineItemsSentToBidder().put("rubicon", new HashSet<>(asList(lineItemId1, lineItemId2)));
        txnLog.lineItemsSentToBidderAsTopMatch().put("rubicon", singleton(lineItemId1));
        txnLog.lineItemsSentToBidderAsTopMatch().put("appnexus", singleton(lineItemId2));
        txnLog.lineItemsReceivedFromBidder().put("rubicon", new HashSet<>(asList(lineItemId1, lineItemId2)));
        txnLog.lineItemsResponseInvalidated().addAll(asList(lineItemId1, lineItemId2));
        txnLog.lostMatchingToLineItems().put(lineItemId1, singleton(lineItemId2));

        // when and then
        deliveryProgressService.processAuctionEvent(AuctionContext.builder()
                .account(Account.empty("1001"))
                .txnLog(txnLog)
                .build());
        deliveryProgressService.createDeliveryProgressReports(now);

        final ArgumentCaptor<DeliveryProgress> deliveryProgressReportCaptor =
                ArgumentCaptor.forClass(DeliveryProgress.class);
        verify(deliveryStatsService).addDeliveryProgress(deliveryProgressReportCaptor.capture(), any());
        final DeliveryProgress deliveryProgress = deliveryProgressReportCaptor.getValue();
        assertThat(deliveryProgress.getRequests().sum()).isEqualTo(151);

        final Set<LineItemStatus> lineItemStatuses = new HashSet<>(deliveryProgress.getLineItemStatuses().values());

        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getDomainMatched, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getTargetMatched, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getTargetMatchedButFcapped, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getTargetMatchedButFcapLookupFailed, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getSentToBidder, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getReceivedFromBidder, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getReceivedFromBidderInvalidated, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getSentToClientAsTopMatch, 151L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getSentToBidderAsTopMatch, 1L, 1L);
        checkLineItemStatusStats(lineItemStatuses, LineItemStatus::getSentToClient, 1L, 1L);

        assertThat(deliveryProgress.getLineItemIdToLost())
                .extracting(lineItemId1)
                .extracting(lineItemId2)
                .extracting(lostToLineItem -> ((LostToLineItem) lostToLineItem).getCount().sum())
                .isEqualTo(1L);

        assertThat(lineItemStatuses)
                .flatExtracting(LineItemStatus::getDeliveryPlans)
                .flatExtracting(DeliveryPlan::getDeliveryTokens)
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal,
                        deliveryToken -> deliveryToken.getSpent().sum())
                .containsOnly(
                        tuple(1, 100, 100L),
                        tuple(2, 100, 51L),
                        tuple(1, 100, 1L),
                        tuple(2, 100, 0L));

        assertThat(lineItem1.getActiveDeliveryPlan().getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsExactly(
                        tuple(1, 100, 100L),
                        tuple(2, 100, 51L));

        assertThat(lineItem2.getActiveDeliveryPlan().getDeliveryTokens())
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal, token -> token.getSpent().sum())
                .containsExactly(
                        tuple(1, 100, 1L),
                        tuple(2, 100, 0L));
    }

    @Test
    public void trackWinEventShouldCreateLineItemStatusAndUpdateWinEventsMetric() {
        // given
        final LineItem lineItem = LineItem.of(
                givenLineItemMetaData(
                        now,
                        lineItemMetaData -> lineItemMetaData
                                .lineItemId("lineItemId1")
                                .accountId("1001")
                                .source("rubicon")
                                .deliverySchedules(singletonList(
                                        givenDeliverySchedule(
                                                "plan1",
                                                now.minusHours(1),
                                                now.plusHours(1),
                                                Set.of(Token.of(1, 100), Token.of(2, 100)))))),
                null,
                null,
                now);

        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(lineItem);

        // when
        deliveryProgressService.processLineItemWinEvent("lineItemId1");

        // then
        // trigger current progress passing to delivery stats
        deliveryProgressService.shutdown();

        final ArgumentCaptor<DeliveryProgress> currentProgressCaptor = ArgumentCaptor.forClass(DeliveryProgress.class);
        verify(deliveryStatsService).addDeliveryProgress(currentProgressCaptor.capture(), any());

        final DeliveryProgress currentProgress = currentProgressCaptor.getValue();
        assertThat(currentProgress).isNotNull();
        assertThat(currentProgress.getLineItemStatuses().entrySet()).hasSize(1)
                .extracting(Map.Entry::getValue)
                .flatExtracting(LineItemStatus::getEvents)
                .extracting(Event::getType, event -> event.getCount().sum())
                .containsOnly(tuple("win", 1L));
    }

    @Test
    public void getLineItemStatusReportShouldReturnExpectedResult() {
        // given
        final LineItem lineItem = LineItem.of(
                givenLineItemMetaData(
                        now,
                        lineItemMetaData -> lineItemMetaData
                                .lineItemId("lineItemId1")
                                .accountId("1001")
                                .source("rubicon")
                                .deliverySchedules(singletonList(
                                        givenDeliverySchedule(
                                                "plan1",
                                                now.minusHours(1),
                                                now.plusHours(1),
                                                singleton(Token.of(1, 100)))))
                                .targeting(mapper.createObjectNode().put("targetingField", "targetingValue"))),
                null,
                null,
                now);
        given(lineItemService.getLineItemById(anyString())).willReturn(lineItem);

        // when
        final LineItemStatusReport report = deliveryProgressService.getLineItemStatusReport("lineItemId1");

        // then
        assertThat(report).isEqualTo(LineItemStatusReport.builder()
                .lineItemId("lineItemId1")
                .deliverySchedule(org.prebid.server.deals.proto.report.DeliverySchedule.builder()
                        .planId("plan1")
                        .planStartTimeStamp("2019-07-26T09:00:00.000Z")
                        .planExpirationTimeStamp("2019-07-26T11:00:00.000Z")
                        .planUpdatedTimeStamp("2019-07-26T09:00:00.000Z")
                        .tokens(singleton(org.prebid.server.deals.proto.report.Token.of(1, 100, 0L, null)))
                        .build())
                .readyToServeTimestamp(now)
                .spentTokens(0L)
                .pacingFrequency(72000L)
                .accountId("1001")
                .target(mapper.createObjectNode().put("targetingField", "targetingValue"))
                .build());
    }

    private static LineItemMetaData givenLineItemMetaData(
            ZonedDateTime now,
            UnaryOperator<LineItemMetaData.LineItemMetaDataBuilder> lineItemMetaDataCustomizer) {

        return lineItemMetaDataCustomizer
                .apply(LineItemMetaData.builder()
                        .dealId("dealId")
                        .status("active")
                        .price(Price.of(BigDecimal.ONE, "USD"))
                        .relativePriority(5)
                        .startTimeStamp(now.minusHours(1))
                        .endTimeStamp(now.plusHours(1))
                        .updatedTimeStamp(now))
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

    private static void checkLineItemStatusStats(Set<LineItemStatus> lineItemStatuses,
                                                 ThrowingExtractor<? super LineItemStatus, LongAdder, Exception> stat,
                                                 Long... values) {
        assertThat(lineItemStatuses)
                .extracting(stat)
                .extracting(LongAdder::sum)
                .containsOnly(values);
    }

    private void recordLineItemsServed(int times, String... lineItemIds) {
        final TxnLog txnLog = TxnLog.create();
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.empty("1001"))
                .txnLog(txnLog)
                .build();
        txnLog.lineItemSentToClientAsTopMatch().addAll(asList(lineItemIds));
        IntStream.range(0, times).forEach(i -> deliveryProgressService.processAuctionEvent(auctionContext));

    }
}
