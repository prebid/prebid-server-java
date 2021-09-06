package org.prebid.server.deals.lineitem;

import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.deals.proto.report.Event;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class DeliveryProgressTest extends VertxTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private Clock clock;

    @Mock
    private LineItemService lineItemService;

    private ZonedDateTime now;

    private DeliveryProgress deliveryProgress;

    @Before
    public void setUp() {
        now = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC));

        given(clock.instant()).willReturn(now.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        deliveryProgress = DeliveryProgress.of(now, lineItemService);
    }

    @Test
    public void cleanLineItemStatusesShouldRemoveOldestCachedPlans() {
        // given
        final TxnLog txnLog1 = TxnLog.create();
        txnLog1.lineItemSentToClientAsTopMatch().add("lineItemId1");

        final TxnLog txnLog2 = TxnLog.create();
        txnLog2.lineItemSentToClientAsTopMatch().add("lineItemId1");

        final TxnLog txnLog3 = TxnLog.create();
        txnLog3.lineItemSentToClientAsTopMatch().add("lineItemId1");

        final LineItem lineItem1 = Mockito.mock(LineItem.class);
        final DeliveryPlan deliveryPlan1 = Mockito.mock(DeliveryPlan.class);
        final DeliveryPlan deliveryPlan2 = Mockito.mock(DeliveryPlan.class);
        final DeliveryPlan deliveryPlan3 = Mockito.mock(DeliveryPlan.class);

        given(deliveryPlan1.getPlanId()).willReturn("lineItemId1Plan");
        given(deliveryPlan2.getPlanId()).willReturn("lineItemId2Plan");
        given(deliveryPlan3.getPlanId()).willReturn("lineItemId3Plan");

        given(deliveryPlan1.withoutSpentTokens()).willReturn(deliveryPlan1);
        given(deliveryPlan2.withoutSpentTokens()).willReturn(deliveryPlan2);
        given(deliveryPlan3.withoutSpentTokens()).willReturn(deliveryPlan3);

        given(deliveryPlan1.getEndTimeStamp()).willReturn(now.minusMinutes(1));
        given(deliveryPlan2.getEndTimeStamp()).willReturn(now.minusMinutes(2));
        given(deliveryPlan3.getEndTimeStamp()).willReturn(now.minusMinutes(3));

        given(lineItem1.getEndTimeStamp()).willReturn(now.minusMinutes(1));
        given(lineItem1.getActiveDeliveryPlan()).willReturn(deliveryPlan1, deliveryPlan2, deliveryPlan3);

        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(lineItem1);

        // when and then
        deliveryProgress.recordTransactionLog(txnLog1, singletonMap("lineItemId1Plan", 1), "1001");
        deliveryProgress.recordTransactionLog(txnLog2, singletonMap("lineItemId2Plan", 1), "1001");
        deliveryProgress.recordTransactionLog(txnLog3, singletonMap("lineItemId3Plan", 1), "1001");

        // check that 3 lineItemStatuses
        assertThat(deliveryProgress.getLineItemStatuses().get("lineItemId1").getDeliveryPlans())
                .hasSize(3)
                .extracting(DeliveryPlan::getPlanId)
                .containsOnly("lineItemId1Plan", "lineItemId2Plan", "lineItemId3Plan");

        deliveryProgress.cleanLineItemStatuses(now, 10000000L, 1);

        assertThat(deliveryProgress.getLineItemStatuses().get("lineItemId1").getDeliveryPlans())
                .hasSize(1)
                .extracting(DeliveryPlan::getPlanId)
                .containsOnly("lineItemId1Plan");
    }

    @Test
    public void cleanLineItemStatusesShouldRemoveExpiredLineItemStatuses() {
        // given
        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemsSentToClient().add("lineItemId1");
        txnLog.lineItemsSentToClient().add("lineItemId2");
        final Map<String, Integer> planIdToTokenPriority = new HashMap<>();
        planIdToTokenPriority.put("lineItemId1Plan", 1);
        planIdToTokenPriority.put("lineItemId2Plan", 1);

        final LineItem lineItem1 = Mockito.mock(LineItem.class);
        final LineItem lineItem2 = Mockito.mock(LineItem.class);

        given(lineItem1.getEndTimeStamp()).willReturn(now.minusHours(1));
        given(lineItem2.getEndTimeStamp()).willReturn(now.minusHours(2));

        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(lineItem1);
        given(lineItemService.getLineItemById(eq("lineItemId2"))).willReturn(lineItem2);

        // when and then
        deliveryProgress.recordTransactionLog(txnLog, planIdToTokenPriority, "1001");

        // check that 2 lineItemStatuses
        assertThat(deliveryProgress.getLineItemStatuses().keySet()).hasSize(2)
                .containsOnly("lineItemId1", "lineItemId2");

        deliveryProgress.cleanLineItemStatuses(now, 6000000, 5);

        assertThat(deliveryProgress.getLineItemStatuses().keySet()).hasSize(1)
                .containsOnly("lineItemId1");
    }

    @Test
    public void fromAnotherCopyingPlansShouldReturnDeliveryProgressWithPlansAndStatistics() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemsMatchedWholeTargeting().add("lineItemId1");
        txnLog.lineItemsMatchedDomainTargeting().add("lineItemId1");
        txnLog.lineItemsReadyToServe().add("lineItemId1");
        txnLog.lineItemsMatchedTargetingFcapped().add("lineItemId1");
        txnLog.lineItemsMatchedTargetingFcapLookupFailed().add("lineItemId1");
        txnLog.lineItemsPacingDeferred().add("lineItemId1");
        txnLog.lineItemsSentToBidder().get("bidder1").add("lineItemId1");
        txnLog.lineItemsSentToBidderAsTopMatch().put("bidder1", singleton("lineItemId1"));
        txnLog.lineItemsReceivedFromBidder().get("bidder1").add("lineItemId1");
        txnLog.lineItemsSentToClient().add("lineItemId1");
        txnLog.lineItemSentToClientAsTopMatch().add("lineItemId1");
        txnLog.lostAuctionToLineItems().put("lineItemId1", singleton("lineItemId2"));
        txnLog.lostMatchingToLineItems().put("lineItemId1", singleton("lineItemId3"));

        final LineItem lineItem = mock(LineItem.class);
        given(lineItemService.getLineItemById("lineItemId1")).willReturn(lineItem);
        given(lineItem.getLineItemId()).willReturn("lineItemId1");
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));

        deliveryProgress.recordTransactionLog(txnLog, singletonMap("planId1", 1), "1001");

        // when
        final DeliveryProgress copiedDeliveryProgress = deliveryProgress.copyWithOriginalPlans();

        // then
        final LineItemStatus lineItemStatusCopied = copiedDeliveryProgress.getLineItemStatuses().get("lineItemId1");
        final LineItemStatus lineItemStatusOriginal = deliveryProgress.getLineItemStatuses().get("lineItemId1");

        assertThat(lineItemStatusCopied).isNotSameAs(lineItemStatusOriginal);

        assertThat(lineItemStatusCopied.getDomainMatched().sum())
                .isEqualTo(lineItemStatusOriginal.getDomainMatched().sum());

        assertThat(lineItemStatusCopied.getTargetMatched().sum())
                .isEqualTo(lineItemStatusOriginal.getTargetMatched().sum());

        assertThat(lineItemStatusCopied.getTargetMatchedButFcapped().sum())
                .isEqualTo(lineItemStatusOriginal.getTargetMatchedButFcapped().sum());

        assertThat(lineItemStatusCopied.getTargetMatchedButFcapLookupFailed().sum())
                .isEqualTo(lineItemStatusOriginal.getTargetMatchedButFcapLookupFailed().sum());

        assertThat(lineItemStatusCopied.getPacingDeferred().sum())
                .isEqualTo(lineItemStatusOriginal.getPacingDeferred().sum());

        assertThat(lineItemStatusCopied.getSentToBidder().sum())
                .isEqualTo(lineItemStatusOriginal.getSentToBidder().sum());

        assertThat(lineItemStatusCopied.getSentToBidderAsTopMatch().sum())
                .isEqualTo(lineItemStatusOriginal.getSentToBidderAsTopMatch().sum());

        assertThat(lineItemStatusCopied.getReceivedFromBidder().sum())
                .isEqualTo(lineItemStatusOriginal.getReceivedFromBidder().sum());

        assertThat(lineItemStatusCopied.getReceivedFromBidderInvalidated().sum())
                .isEqualTo(lineItemStatusOriginal.getReceivedFromBidderInvalidated().sum());

        assertThat(lineItemStatusCopied.getSentToClient().sum())
                .isEqualTo(lineItemStatusOriginal.getSentToClient().sum());

        assertThat(lineItemStatusCopied.getSentToClientAsTopMatch().sum())
                .isEqualTo(lineItemStatusOriginal.getSentToClientAsTopMatch().sum());

        assertThat(lineItemStatusCopied.getSentToClientAsTopMatch().sum())
                .isEqualTo(lineItemStatusOriginal.getSentToClientAsTopMatch().sum());

        assertThat(copiedDeliveryProgress.getLineItemIdToLost().get("lineItemId1").entrySet())
                .extracting(Map.Entry::getKey, entry -> entry.getValue().getCount().sum())
                .containsOnly(Tuple.tuple("lineItemId2", 1L), Tuple.tuple("lineItemId3", 1L));

        final DeliveryPlan originDeliveryPlan = lineItemStatusOriginal.getDeliveryPlans().stream().findFirst().get();
        final DeliveryPlan copiedDeliveryPlan = lineItemStatusCopied.getDeliveryPlans().stream().findFirst().get();
        assertThat(originDeliveryPlan).isSameAs(copiedDeliveryPlan);
    }

    @Test
    public void recordWinEventShouldRecordEventForAbsentInReportLineItemStatus() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);

        // when
        deliveryProgress.recordWinEvent("lineItemId1");

        // then
        final LineItemStatus lineItemStatus = deliveryProgress.getLineItemStatuses().get("lineItemId1");
        assertThat(lineItemStatus.getEvents())
                .extracting(Event::getType, event -> event.getCount().sum())
                .containsOnly(Tuple.tuple("win", 1L));
    }

    @Test
    public void upsertPlanReferenceFromLineItemShouldInsertReferenceToNotExistingLineItemStatus() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        final LineItem lineItem = mock(LineItem.class);
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));
        given(lineItem.getLineItemId()).willReturn("lineItemId1");

        // when
        deliveryProgress.upsertPlanReferenceFromLineItem(lineItem);

        // then
        assertThat(deliveryProgress.getLineItemStatuses()).hasSize(1);
        final Set<DeliveryPlan> deliveryPlans = deliveryProgress.getLineItemStatuses().get("lineItemId1")
                .getDeliveryPlans();
        assertThat(deliveryPlans).hasSize(1).extracting(DeliveryPlan::getDeliverySchedule)
                .containsOnly(givenDeliverySchedule(now, "planId1"));
    }

    @Test
    public void upsertPlanReferenceFromLineItemShouldInsertToExistingWhenNotContainsPlanWithSameId() {
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        deliveryProgress.getLineItemStatuses().put("lineItemId1", LineItemStatus.of("lineItemId1"));
        final LineItem lineItem = mock(LineItem.class);
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));
        given(lineItem.getLineItemId()).willReturn("lineItemId1");

        // when
        deliveryProgress.upsertPlanReferenceFromLineItem(lineItem);

        // then
        assertThat(deliveryProgress.getLineItemStatuses()).hasSize(1);
        final Set<DeliveryPlan> deliveryPlans = deliveryProgress.getLineItemStatuses().get("lineItemId1")
                .getDeliveryPlans();
        assertThat(deliveryPlans).hasSize(1).extracting(DeliveryPlan::getDeliverySchedule)
                .containsOnly(givenDeliverySchedule(now, "planId1"));
    }

    @Test
    public void upsertPlanReferenceFromLineItemShouldReplacePlanWhenContainsPlanWithSameId() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        final LineItemStatus lineItemStatus = LineItemStatus.of("lineItemId1");
        lineItemStatus.getDeliveryPlans().add(DeliveryPlan.of(givenDeliverySchedule(now.minusMinutes(1), "planId1")));
        deliveryProgress.getLineItemStatuses().put("lineItemId1", lineItemStatus);
        final LineItem lineItem = mock(LineItem.class);
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));
        given(lineItem.getLineItemId()).willReturn("lineItemId1");

        // when
        deliveryProgress.upsertPlanReferenceFromLineItem(lineItem);

        // then
        assertThat(deliveryProgress.getLineItemStatuses()).hasSize(1);
        final Set<DeliveryPlan> deliveryPlans = deliveryProgress.getLineItemStatuses().get("lineItemId1")
                .getDeliveryPlans();
        assertThat(deliveryPlans).hasSize(1).extracting(DeliveryPlan::getDeliverySchedule)
                .containsOnly(givenDeliverySchedule(now, "planId1"));
    }

    @Test
    public void mergePlanFromLineItemShouldMergeCurrentPlanWithNewActiveOne() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        final LineItemStatus lineItemStatus = LineItemStatus.of("lineItemId1");
        lineItemStatus.getDeliveryPlans().add(DeliveryPlan.of(DeliverySchedule.builder().planId("planId1")
                .startTimeStamp(now.minusMinutes(1)).endTimeStamp(now.plusMinutes(1))
                .updatedTimeStamp(now.minusMinutes(2))
                .tokens(singleton(Token.of(2, 50))).build()));
        deliveryProgress.getLineItemStatuses().put("lineItemId1", lineItemStatus);
        final LineItem lineItem = mock(LineItem.class);
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));
        given(lineItem.getLineItemId()).willReturn("lineItemId1");

        // when
        deliveryProgress.mergePlanFromLineItem(lineItem);

        // then
        assertThat(deliveryProgress.getLineItemStatuses()).hasSize(1);
        final Set<DeliveryPlan> deliveryPlans = deliveryProgress.getLineItemStatuses().get("lineItemId1")
                .getDeliveryPlans();
        assertThat(deliveryPlans).hasSize(1)
                .flatExtracting(DeliveryPlan::getDeliveryTokens)
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal,
                        deliveryToken -> deliveryToken.getSpent().sum())
                .containsOnly(Tuple.tuple(1, 100, 0L), Tuple.tuple(2, 50, 0L));

        assertThat(deliveryPlans).hasSize(1)
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getUpdatedTimeStamp)
                .containsOnly(Tuple.tuple("planId1", now.minusMinutes(1)));
    }

    @Test
    public void mergePlanFromLineItemShouldReplaceCurrentPlanWithNewActiveOneWithoutSpentTokens() {
        // given
        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now, lineItemService);
        deliveryProgress.getLineItemStatuses().put("lineItemId1", LineItemStatus.of("lineItemId1"));
        final LineItem lineItem = mock(LineItem.class);
        given(lineItem.getActiveDeliveryPlan()).willReturn(DeliveryPlan.of(givenDeliverySchedule(now, "planId1")));
        given(lineItem.getLineItemId()).willReturn("lineItemId1");

        // when
        deliveryProgress.mergePlanFromLineItem(lineItem);

        // then
        assertThat(deliveryProgress.getLineItemStatuses()).hasSize(1);
        final Set<DeliveryPlan> deliveryPlans = deliveryProgress.getLineItemStatuses().get("lineItemId1")
                .getDeliveryPlans();
        assertThat(deliveryPlans).hasSize(1)
                .flatExtracting(DeliveryPlan::getDeliveryTokens)
                .extracting(DeliveryToken::getPriorityClass, DeliveryToken::getTotal,
                        deliveryToken -> deliveryToken.getSpent().sum())
                .containsOnly(Tuple.tuple(1, 100, 0L));

        assertThat(deliveryPlans).hasSize(1)
                .extracting(DeliveryPlan::getPlanId, DeliveryPlan::getUpdatedTimeStamp)
                .containsOnly(Tuple.tuple("planId1", now.minusMinutes(1)));
    }

    private static DeliverySchedule givenDeliverySchedule(ZonedDateTime now, String planId) {
        return DeliverySchedule.builder()
                .planId(planId)
                .startTimeStamp(now.minusMinutes(1))
                .endTimeStamp(now.plusMinutes(1))
                .updatedTimeStamp(now.minusMinutes(1))
                .tokens(singleton(Token.of(1, 100)))
                .build();
    }
}
