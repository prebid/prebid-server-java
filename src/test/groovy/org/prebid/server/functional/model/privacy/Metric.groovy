package org.prebid.server.functional.model.privacy

import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest

enum Metric {

    ALERT_GENERAL("alerts.general"),
    PROCESSED_RULES_COUNT("requests.activity.processedrules.count"),
    ACCOUNT_PROCESSED_RULES_COUNT("requests.activity.processedrules.count"),
    ADAPTER_DISALLOWED_COUNT("adapter.{bidderName}.activity.{activityType}.disallowed.count"),
    ACCOUNT_DISALLOWED_COUNT("account.{accountId}.activity.{activityType}.disallowed.count"),
    REQUEST_DISALLOWED_COUNT("requests.activity.{activityType}.disallowed.count"),

    final String value

    Metric(String value) {
        this.value = value
    }

    String getValue(BidRequest bidRequest, ActivityType activityType) {
        if (bidRequest.imp.size() != 1) {
            throw new IllegalStateException("No bidder found")
        }
        def bidderName = bidRequest.imp.first.bidderName.value
        def accountId = bidRequest.accountId
        replaceValues(bidderName, accountId, activityType.metricValue)
    }

    String getValue(CookieSyncRequest syncRequest, ActivityType activityType) {
        if (syncRequest.bidders.size() != 1) {
            throw new IllegalStateException("No bidder found")
        }
        def bidderName = syncRequest.bidders.first.value
        def accountId = syncRequest.account
        replaceValues(bidderName, accountId, activityType.metricValue)
    }

    String getValue(SetuidRequest syncRequest, ActivityType activityType) {
        def bidderName = syncRequest.bidder.value
        def accountId = syncRequest.account
        replaceValues(bidderName, accountId, activityType.metricValue)
    }

    private String replaceValues(String bidderName, String accountId, String activityType) {
        this.value.replaceAll('\\{bidderName}', bidderName)
                .replaceAll('\\{accountId}', accountId)
                .replaceAll('\\{activityType}', activityType)
    }
}
