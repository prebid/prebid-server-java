package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.model.deals.lineitem.LineItem.TIME_PATTERN

@ToString(includeNames = true, ignoreNulls = true)
class DeliverySchedule {

    String planId

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime startTimeStamp

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime endTimeStamp

    @JsonFormat(pattern = TIME_PATTERN)
    ZonedDateTime updatedTimeStamp

    Set<Token> tokens

    static getDefaultDeliverySchedule() {
        new DeliverySchedule(planId: PBSUtils.randomString,
                startTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                endTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)).plusDays(1),
                updatedTimeStamp: ZonedDateTime.now(ZoneId.from(UTC)),
                tokens: [Token.defaultToken]
        )
    }
}
