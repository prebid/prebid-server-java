package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ACCOUNT_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.ACCOUNT_PROCESSED_RULES_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.PROCESSED_ACTIVITY_RULES_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_TID
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class GppTransmitTidActivitiesSpec extends PrivacyBaseSpec {

    def "PBS auction should generate id for bidRequest.(source/imp[0].ext).tid when ext.prebid.createTids=null and transmit activity allowed"() {
        given: "Bid requests without TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.tap {
                trace = VERBOSE
                createTids = null
            }
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should generate (source/imp.ext).tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, TRANSMIT_TID)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, TRANSMIT_TID)] == 1

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity),
                       new AllowActivities().tap { transmitTidKebabCase = Activity.defaultActivity },
                       new AllowActivities().tap { transmitTidSnakeCase = Activity.defaultActivity },
        ]
    }

    def "PBS auction should generate id for bidRequest.(source/imp[0].ext).tid when ext.prebid.createTids=true and transmit activity"() {
        given: "Bid requests without TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.tap {
                trace = VERBOSE
                createTids = true
            }
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
        }

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should generate (source/imp.ext).tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])),
                       new AllowActivities().tap { transmitTidKebabCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
                       new AllowActivities().tap { transmitTidSnakeCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
        ]
    }

    def "PBS auction shouldn't generate id for bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=false and transmit activity allowed and schain specified in request"() {
        given: "Bid requests without TID fields and with schain fields, account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = null
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = false
            }
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't generate (source/imp.ext).tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.imp[0].ext.tid
            !bidderRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain
    }

    def "PBS auction shouldn't generate id for bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=#createTid, transmit activity allowed, bidRequest.(source/imp[0].ext).tid and schain are specified"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = createTid
            }
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't generate (source/imp.ext).tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid == bidRequest.imp[0].ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain

        where:
        createTid << [true, null]
    }

    def "PBS auction should remove bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=null and activity disallowed and schain specified in reques"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = null
            }
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp[0].ext.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_TID)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_TID)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_TID)] == 1
    }

    def "PBS auction should remove bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=#createTid and defaultAction=false and schain specified in request"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = createTid
            }
        }

        and: "Activities set with default action false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.imp[0].ext.tid
            !bidderRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain

        where:
        createTid << [false, null]
    }

    def "PBS auction should skip rule and emit an error when allowed bidder activities have invalid condition type"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Logs should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                                | isAllowed
        new Condition(componentType: [])          | true
        new Condition(componentType: [])          | false
        new Condition(componentName: [])          | true
        new Condition(componentName: [])          | false
        new Condition(componentTypeKebabCase: []) | true
        new Condition(componentTypeKebabCase: []) | false
        new Condition(componentNameKebabCase: []) | true
        new Condition(componentNameKebabCase: []) | false
        new Condition(componentTypeSnakeCase: []) | true
        new Condition(componentTypeSnakeCase: []) | false
        new Condition(componentNameSnakeCase: []) | true
        new Condition(componentNameSnakeCase: []) | false
    }

    def "PBS auction should generate bidRequest.(source/imp[0].ext).tid when first rule allow=true and bidRequest.(source/imp[0].ext).tid=null"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }
    }

    def "PBS auction should remove bidRequest.(source/imp[0].ext).tid when first rule allow=false and bidRequest.(source/imp[0].ext).tid has value"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp[0].ext.tid
        }
    }

    def "PBS amp should generate id for bidRequest.(source/imp[0].ext).tid when ext.prebid.createTids=null and transmit activity allowed"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
            ext.prebid.tap {
                trace = VERBOSE
                createTids = null
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(ampStoredRequest, TRANSMIT_TID)] == 1
    }

    def "PBS amp should generate id for bidRequest.(source/imp[0].ext).tid when ext.prebid.createTids=true and transmit activity allowed"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
            ext.prebid.tap {
                trace = VERBOSE
                createTids = true
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }
    }

    def "PBS amp should generate id for bidRequest.(source/imp[0].ext).tid when ext.prebid.createTids=true and transmit activity disallowed"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
            ext.prebid.tap {
                trace = VERBOSE
                createTids = true
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder disallowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }
    }

    def "PBS amp shouldn't generate id for bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=false and transmit activity allowed and schain specified in request"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = null
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = false
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder disallowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.imp[0].ext.tid
            !bidderRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain
    }

    def "PBS amp shouldn't generate id for bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=createTid and transmit activity allowed and bidRequest.(source/imp[0].ext).tid are specified"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = createTid
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain from incoming source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid == ampStoredRequest.imp[0].ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain

        where:
        createTid << [true, null]
    }

    def "PBS amp should remove bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=null and activity disallowed and schain specified in request"() {
        given: "Bid requests with TID, schain fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = null
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.imp[0].ext.tid
            !bidderRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain
    }

    def "PBS amp should remove bidRequest.(source/imp[0].ext).tid and don't change schain in request when ext.prebid.createTids=#createTid and defaultAction=false and schain specified in request"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def sourceWithSchainAndTid = Source.defaultSource.tap {
            tid = PBSUtils.randomString
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = sourceWithSchainAndTid
            ext.prebid.tap {
                trace = VERBOSE
                createTids = null
            }
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with default action false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.imp[0].ext.tid
            !bidderRequest.source.tid
        }

        and: "Don’t affect source.schain if it was present"
        assert bidderRequest.source.schain == sourceWithSchainAndTid.schain
    }

    def "PBS amp should skip rule and emit an error when allowed bidder activities have invalid condition type"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Logs should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS amp should generate bidRequest.(source/imp[0].ext).tid when first rule allow=true and bidRequest.(source/imp[0].ext).tid=null"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp[0].ext.tid = null
            source = new Source(tid: null)
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should generate source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp[0].ext.tid
            bidderRequest.source.tid
        }
    }

    def "PBS amp should remove bidRequest.(source/imp[0].ext).tid when first rule allow=false and bidRequest.(source/imp[0].ext).tid has value"() {
        given: "Bid requests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp[0].ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp[0].ext.tid
        }
    }
}
