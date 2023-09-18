package org.prebid.server.deals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.lineitem.LineItemStatus;
import org.prebid.server.deals.lineitem.LostToLineItem;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.deals.proto.report.DeliveryProgressReportBatch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class DeliveryProgressReportFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LineItemService lineItemService;

    private DeliveryProgressReportFactory deliveryProgressReportFactory;

    private ZonedDateTime now;

    @Before
    public void setUp() {
        deliveryProgressReportFactory = new DeliveryProgressReportFactory(
                DeploymentProperties.builder().pbsHostId("pbsHost").pbsRegion("pbsRegion")
                        .pbsVendor("pbsVendor").build(), 2, lineItemService);

        now = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    public void fromDeliveryProgressShouldCreateReportWithTop2Competitors() {
        // given
        given(lineItemService.getLineItemById(anyString()))
                .willReturn(LineItem.of(
                        LineItemMetaData.builder()
                                .accountId("accountId")
                                .deliverySchedules(singletonList(DeliverySchedule.builder()
                                        .startTimeStamp(now.minusHours(1))
                                        .endTimeStamp(now.plusHours(1))
                                        .updatedTimeStamp(now.minusHours(1))
                                        .build()))
                                .source("rubicon")
                                .build(),
                        null, null, now));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);
        deliveryProgress.setEndTimeStamp(now.minusHours(2));

        final Map<String, LostToLineItem> lostTo = new ConcurrentHashMap<>();
        lostTo.put("lineItemId1", LostToLineItem.of("lineItemId1", makeLongAdderWithValue(100L)));
        lostTo.put("lineItemId2", LostToLineItem.of("lineItemId2", makeLongAdderWithValue(50L)));
        lostTo.put("lineItemId3", LostToLineItem.of("lineItemId3", makeLongAdderWithValue(80L)));
        lostTo.put("lineItemId4", LostToLineItem.of("lineItemId4", makeLongAdderWithValue(120L)));
        deliveryProgress.getLineItemIdToLost().put("lineItemId5", lostTo);
        deliveryProgress.getLineItemStatuses().put("lineItemId5", LineItemStatus.of("lineItemId5"));

        // when
        final DeliveryProgressReport deliveryProgressReport = deliveryProgressReportFactory
                .fromDeliveryProgress(deliveryProgress, now, false);

        // then
        assertThat(deliveryProgressReport.getLineItemStatus())
                .flatExtracting(org.prebid.server.deals.proto.report.LineItemStatus::getLostToLineItems)
                .extracting(org.prebid.server.deals.proto.report.LostToLineItem::getLineItemSource,
                        org.prebid.server.deals.proto.report.LostToLineItem::getLineItemId,
                        org.prebid.server.deals.proto.report.LostToLineItem::getCount)
                .containsOnly(
                        tuple("rubicon", "lineItemId4", 120L),
                        tuple("rubicon", "lineItemId1", 100L));
    }

    @Test
    public void fromDeliveryProgressShouldCreateOverallReport() {
        // given
        given(lineItemService.getLineItemById(anyString()))
                .willReturn(LineItem.of(
                        LineItemMetaData.builder()
                                .accountId("accountId")
                                .deliverySchedules(singletonList(DeliverySchedule.builder()
                                        .startTimeStamp(now.minusHours(1))
                                        .endTimeStamp(now.plusHours(1))
                                        .updatedTimeStamp(now.minusHours(1))
                                        .tokens(singleton(Token.of(1, 100)))
                                        .build()))
                                .source("rubicon")
                                .build(),
                        null, null, now));

        final DeliveryProgress deliveryProgress = mock(DeliveryProgress.class);
        given(deliveryProgress.getRequests()).willReturn(new LongAdder());
        given(deliveryProgress.getLineItemStatuses()).willReturn(singletonMap("lineItemId1",
                LineItemStatus.of("lineItemId1")));
        deliveryProgress.setEndTimeStamp(now.minusHours(2));

        // when
        final DeliveryProgressReport deliveryProgressReport = deliveryProgressReportFactory
                .fromDeliveryProgress(deliveryProgress, now, true);

        // then
        assertThat(deliveryProgressReport.getLineItemStatus())
                .extracting(org.prebid.server.deals.proto.report.LineItemStatus::getReadyAt,
                        org.prebid.server.deals.proto.report.LineItemStatus::getPacingFrequency,
                        org.prebid.server.deals.proto.report.LineItemStatus::getSpentTokens)
                .containsOnly(tuple("2019-07-26T10:00:00.000Z", 72000L, 0L));
    }

    @Test
    public void fromDeliveryProgressShouldDropLineItemsWithoutDeliverySchedule() {
        // given
        given(lineItemService.getLineItemById(anyString()))
                .willReturn(LineItem.of(
                        LineItemMetaData.builder()
                                .accountId("accountId")
                                .source("rubicon")
                                .build(),
                        null, null, now));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);
        deliveryProgress.setEndTimeStamp(now.minusHours(2));

        // when
        final DeliveryProgressReport deliveryProgressReport = deliveryProgressReportFactory
                .fromDeliveryProgress(deliveryProgress, now, false);

        // then
        assertThat(deliveryProgressReport.getLineItemStatus()).isEmpty();
    }

    @Test
    public void batchFromDeliveryProgressShouldCreateTwoReportsInBatchWithSameId() {
        // given
        given(lineItemService.getLineItemById(anyString()))
                .willReturn(LineItem.of(
                        LineItemMetaData.builder()
                                .accountId("accountId")
                                .deliverySchedules(singletonList(DeliverySchedule.builder()
                                        .startTimeStamp(now.minusHours(1))
                                        .endTimeStamp(now.plusHours(1))
                                        .updatedTimeStamp(now.minusHours(1))
                                        .build()))
                                .source("rubicon")
                                .build(),
                        null, null, now));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);
        deliveryProgress.setEndTimeStamp(now.minusHours(2));

        deliveryProgress.getLineItemStatuses().put("lineItemId1", LineItemStatus.of("lineItemId1"));
        deliveryProgress.getLineItemStatuses().put("lineItemId2", LineItemStatus.of("lineItemId2"));
        deliveryProgress.getLineItemStatuses().put("lineItemId3", LineItemStatus.of("lineItemId3"));

        // when
        final DeliveryProgressReportBatch deliveryProgressReportBatch = deliveryProgressReportFactory
                .batchFromDeliveryProgress(deliveryProgress, null, now, 2, false);

        // then
        final Set<DeliveryProgressReport> reports = deliveryProgressReportBatch.getReports();
        assertThat(reports).hasSize(2)
                .extracting(DeliveryProgressReport::getReportId)
                .containsOnly(deliveryProgressReportBatch.getReportId());
        assertThat(reports)
                .extracting(deliveryProgressReport -> deliveryProgressReport.getLineItemStatus().size())
                .containsOnly(1, 2);
    }

    private static LongAdder makeLongAdderWithValue(Long value) {
        final LongAdder longAdder = new LongAdder();
        longAdder.add(value);
        return longAdder;
    }
}
