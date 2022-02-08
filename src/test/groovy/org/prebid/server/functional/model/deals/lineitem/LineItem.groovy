package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.ToString
import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static LineItemStatus.ACTIVE
import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.deals.lineitem.RelativePriority.VERY_HIGH

@ToString(includeNames = true, ignoreNulls = true)
class LineItem {

    public static final String TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    String lineItemId

    String extLineItemId

    String dealId

    List<LineItemSize> sizes

    String accountId

    String source

    Price price

    RelativePriority relativePriority

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime startTimeStamp

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime endTimeStamp

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime updatedTimeStamp

    LineItemStatus status

    List<FrequencyCap> frequencyCaps

    List<DeliverySchedule> deliverySchedules

    Targeting targeting

    static LineItem getDefaultLineItem(String accountId) {
        int plannerAdapterLineItemId = PBSUtils.randomNumber
        String plannerAdapterName = PBSUtils.randomString
        new LineItem(lineItemId: "${plannerAdapterName}-$plannerAdapterLineItemId",
                extLineItemId: plannerAdapterLineItemId,
                dealId: PBSUtils.randomString,
                sizes: [LineItemSize.defaultLineItemSize],
                accountId: accountId,
                source: GENERIC.name().toLowerCase(),
                price: Price.defaultPrice,
                relativePriority: VERY_HIGH,
                startTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                endTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusMonths(1),
                updatedTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                status: ACTIVE,
                frequencyCaps: [],
                deliverySchedules: [DeliverySchedule.defaultDeliverySchedule],
                targeting: Targeting.defaultTargeting
        )
    }
}
