package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_TID
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class GppTransmitTidActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_TID.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String ALERT_GENERAL = "alert.general"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_TID.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_TID.metricValue}.disallowed.count"

    def "PBS auction call when bidder allowed in activities should leave tid fields in request and update processed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with generic bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when bidder rejected in activities should remove tid fields in request and update disallowed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with bidder allowed"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.imp.ext.tid
            !bidderRequest.source.tid
        }
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS auction call when first rule allowing in activities should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }
    }

    def "PBS auction call when first rule disallowing in activities should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS auction call when privacy regulation match and disabled should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS auction call when privacy regulation restring but sid excluded should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1])

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }
    }

    def "PBS auction call when privacy regulation not exist for account and allowing should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with non-existed privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Existed account with empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }
    }

    def "PBS auction call when privacy regulation have duplicate should include first, leave tid fields in request and populate metric"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } == ["Invalid allowActivities config for account: " + accountId]
        // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }
    }

    def "PBS auction call when privacy regulation match and rejecting should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS auction call when privacy regulation match and rejecting by element in hierarchy should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with rejecting privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS auction call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with multiple array").size() == 1
    }

    def "PBS amp call when bidder allowed in activities should leave tid fields in request and update processed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when bidder rejected in activities should remove tid fields in request and update disallowed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Reject activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS amp call when first rule allowing in activities should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }
    }

    def "PBS amp call when first rule disallowing in activities should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS amp call when privacy regulation match and disabled should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS amp call when privacy regulation restring but sid excluded should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1])

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }
    }

    def "PBS amp call when privacy regulation not exist for account should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with all bidders allowed"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        and: "Account gpp configuration"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Existed account with empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }
    }

    def "PBS amp call when privacy regulation have duplicate should include first, leave tid fields in request and populate metric"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } ==
                ["Invalid allowActivities config for account: " + accountId] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when privacy regulation match and rejecting should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.imp.ext.tid
            !bidderRequest.source.tid
        }
    }

    def "PBS amp call when privacy regulation match and rejecting by element in hierarchy should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit tid with multiple privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS amp call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([rule]))

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with multiple array").size() == 1
    }
}
