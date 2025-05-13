package org.prebid.server.functional.model.privacy

import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest

enum Metric {

    ALERT_GENERAL("alerts.general"),
    PROCESSED_ACTIVITY_RULES_COUNT("requests.activity.processedrules.count"),
    ACCOUNT_PROCESSED_RULES_COUNT("requests.activity.processedrules.count"),
    TEMPLATE_ADAPTER_DISALLOWED_COUNT("adapter.{bidderName}.activity.{activityType}.disallowed.count"),
    TEMPLATE_ACCOUNT_DISALLOWED_COUNT("account.{accountId}.activity.{activityType}.disallowed.count"),
    TEMPLATE_REQUEST_DISALLOWED_COUNT("requests.activity.{activityType}.disallowed.count"),

    final String value

    Metric(String value) {
        this.value = value
    }

    String getValue(BidRequest bidRequest, String accountId, ActivityType activityType) {
        if (bidRequest.imp.size() != 1) {
            throw new IllegalStateException("No imp found")
        }
        replaceValues(bidRequest.imp.first.bidderName.value, accountId, activityType.metricValue)
    }

    String getValue(BidRequest bidRequest, ActivityType activityType) {
        if (bidRequest.imp.size() != 1) {
            throw new IllegalStateException("No imp found")
        }
        replaceValues(bidRequest.imp.first.bidderName.value, bidRequest.accountId, activityType.metricValue)
    }

    String getValue(CookieSyncRequest syncRequest, ActivityType activityType) {
        if (syncRequest.bidders.size() != 1) {
            throw new IllegalStateException("No bidder found")
        }
        replaceValues(syncRequest.bidders.first.value, syncRequest.account, activityType.metricValue)
    }

    String getValue(SetuidRequest syncRequest, ActivityType activityType) {
        replaceValues(syncRequest.bidder.value, syncRequest.account, activityType.metricValue)
    }

    private String replaceValues(String bidderName, String accountId, String activityType) {
        this.value.replaceAll('\\{bidderName}', bidderName)
                .replaceAll('\\{accountId}', accountId)
                .replaceAll('\\{activityType}', activityType)
    }
}
