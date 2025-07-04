package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountProfilesConfigs
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredProfileImp
import org.prebid.server.functional.model.db.StoredProfileRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtPrebid
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.profile.Profile
import org.prebid.server.functional.model.request.profile.ProfileImp
import org.prebid.server.functional.model.request.profile.ProfileRequest
import org.prebid.server.functional.model.request.profile.ProfileType
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.NoBidResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.repository.dao.ProfileImpDao
import org.prebid.server.functional.repository.dao.ProfileRequestDao
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.profile.ProfileMergePrecedence.PROFILE
import static org.prebid.server.functional.model.request.profile.ProfileMergePrecedence.REQUEST
import static org.prebid.server.functional.model.request.profile.ProfileMergePrecedence.UNKNOWN
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class ProfileSpec extends BaseSpec {

    private static final String PROFILES_PATH = '/app/prebid-server/profiles'
    private static final Integer LIMIT_HOST_PROFILE = 2

    private static final Map<String, String> PROFILES_CONFIG = [
            "adapters.openx.enabled"          : "true",
            "adapters.openx.endpoint"         : "$networkServiceContainer.rootUri/openx/auction".toString(),
            "auction.profiles.limit"          : LIMIT_HOST_PROFILE.toString(),
            "auction.profiles.fail-on-unknown": "false",
            "settings.filesystem.profiles-dir": PROFILES_PATH,
            "settings.database.profiles-query": 'SELECT profileName, reqId, mergePrecedence, profileType, profileBody FROM profiles_profile WHERE reqId IN (%REQUEST_ID_LIST%)']
    ProfileImpDao profileImpDao = repository.profileImpDao
    ProfileRequestDao profileRequestDao = repository.profileRequestDao
    private static PrebidServerService pbsWithStoredProfiles = PbsServiceFactory.getService(PROFILES_CONFIG)
    private static final String REJECT_ERROR_MESSAGE = 'replace'
    private static final String MISSING_ERROR_MESSAGE = 'replace'
    private static final String REJECT_PROFILE_METRIC = 'profile.rejected'
    private static final String REJECT_ACCOUNT_PROFILE_METRIC = "account.%s.profile.rejected"
    private static final String MISSING_PROFILE_METRIC = 'profile.rejected'
    private static final String MISSING_ACCOUNT_PROFILE_METRIC = "account.%s.profile.rejected"

    def cleanupSpec() {
        PbsServiceFactory.removeContainer(PROFILES_CONFIG)
    }

    def "PBS should use profile for request when it exist in database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ProfileRequest.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == requestProfile.body.site
            it.device == requestProfile.body.device
        }
    }

    def "PBS should use imp profile for request when it exist in database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ProfileImp.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]).tap {
            imp.first.banner = null
        } as BidRequest

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp.first == impProfile.body
    }

    def "PBS should use profile for request when it exist in filesystem"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def profile = ProfileRequest.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [profile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Profile fine in PBS container"
        pbsWithStoredProfiles.copyToContainer(encode(profile), "$PROFILES_PATH/${profile.fileName}")

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == profile.body.site
            it.device == profile.body.device
        }
    }

    def "PBS should use imp profile for request when it exist in filesystem"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def profile = ProfileImp.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [profile]).tap {
            imp.first.banner = null
        } as BidRequest

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Profile fine in PBS container"
        pbsWithStoredProfiles.copyToContainer(encode(profile), "$PROFILES_PATH/${profile.fileName}")

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp.first == profile.body
    }

    def "PBS should emit error for request when same profile exist in filesystem and database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def profile = ProfileRequest.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [profile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Profile fine in PBS container"
        pbsWithStoredProfiles.copyToContainer(encode(profile), "$PROFILES_PATH/${profile.fileName}")

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(profile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [REJECT_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(REJECT_ERROR_MESSAGE)

        and: "Reject metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[REJECT_PROFILE_METRIC] == 1
        assert metrics[REJECT_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }
    }

    def "PBS should prioritise original request data over profile when merge strategy #mergeStrategy"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ProfileRequest.getProfile(accountId).tap {
            mergePrecedence = mergeStrategy
        }
        BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profilesNames = [requestProfile.name]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == requestProfile.body.site
            it.device == requestProfile.body.device
        }

        where:
        mergeStrategy << [null, UNKNOWN, REQUEST]
    }

    def "PBS should prioritise original imp data over profile when merge strategy #mergeStrategy"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ProfileImp.getProfile(accountId).tap {
            mergePrecedence = mergeStrategy
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp == [impProfile.body]

        where:
        mergeStrategy << [null, UNKNOWN, REQUEST]
    }

    def "PBS should marge earliest-specified profile profile with request merge precedence when there marge conflict"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = getRequestWithProfiles(accountId, [firstProfile, secondProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profiles"
        def mergedRequest = [firstProfile, secondProfile].find { it.mergePrecedence == REQUEST }.body
        assert bidder.getBidderRequest(bidRequest.id).site == mergedRequest.site

        where:
        firstProfile                                                           | secondProfile
        ProfileRequest.getProfile(accountId).tap { mergePrecedence = REQUEST } | ProfileRequest.getProfile(accountId)
        ProfileRequest.getProfile(accountId)                                   | ProfileRequest.getProfile(accountId).tap { mergePrecedence = REQUEST }
        ProfileRequest.getProfile(accountId).tap { mergePrecedence = REQUEST } | ProfileRequest.getProfile(accountId).tap { mergePrecedence = REQUEST }
    }

    def "PBS should marge latest-specified profile profile with request merge precedence when there marge conflict"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def firstRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.device = Device.default
            body.site = Site.defaultSite
        }
        def secondRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.device = Device.default
            body.site = Site.defaultSite
        }
        def bidRequest = getRequestWithProfiles(accountId, [firstRequestProfile, secondRequestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondRequestProfile))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profiles"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == secondRequestProfile.body.site
            it.device == secondRequestProfile.body.device
        }
    }

    def "PBS should prioritise profile for request when request is overloaded by profiles"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def profileSite = Site.defaultSite
        def profileDevice = Device.default
        def firstRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.site = profileSite
        }
        def secondRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.device = profileDevice
        }
        def impProfile = ProfileImp.getProfile(accountId, Imp.getDefaultImpression(VIDEO))
        def bidRequest = getRequestWithProfiles(accountId, [impProfile, firstRequestProfile, secondRequestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondRequestProfile))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_PROFILE_METRIC] == 1
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profiles"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site == profileSite
            it.device == profileDevice
        }

        and: "Bidder imp should contain original data from request"
        assert bidderRequest.imp == bidRequest.imp
    }

    def "PBS should be able override profile limit by account config and use remaining limits for each imp separately"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def profileSite = Site.defaultSite
        def profileDevice = Device.default
        def firstRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.site = profileSite
        }
        def secondRequestProfile = ProfileRequest.getProfile(accountId).tap {
            body.device = profileDevice
        }
        def firstImpProfile = ProfileImp.getProfile(accountId)
        def secondImpProfile = ProfileImp.getProfile(accountId, Imp.getDefaultImpression(VIDEO))
        def thirdImpProfile = ProfileImp.getProfile(accountId, Imp.getDefaultImpression(NATIVE))
        def bidRequest = getRequestWithProfiles(accountId, [firstImpProfile, secondImpProfile, firstRequestProfile, secondRequestProfile]).tap {
            imp << new Imp(ext: new ImpExt(prebid: new ImpExtPrebid(profilesNames: [secondImpProfile, thirdImpProfile].name)))
        } as BidRequest

        and: "Default account"
        def profilesConfigs = new AccountProfilesConfigs(limit: LIMIT_HOST_PROFILE + 2)
        def accountAuctionConfig = new AccountAuctionConfig(profiles: profilesConfigs)
        def accountConfig = new AccountConfig(auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE, config: accountConfig)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondRequestProfile))
        profileImpDao.save(StoredProfileImp.getProfile(firstImpProfile))
        profileImpDao.save(StoredProfileImp.getProfile(secondImpProfile))
        profileImpDao.save(StoredProfileImp.getProfile(thirdImpProfile))
        profileImpDao.save(StoredProfileImp.getProfile(fourthImpProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric shouldn't increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert !metrics[MISSING_PROFILE_METRIC]
        assert !metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)]

        and: "Bidder request should contain data from profiles"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site == profileSite
            it.device == profileDevice
        }

        and: "Bidder imps should contain data from profiles"
        assert bidderRequest.imp.first.banner == firstImpProfile.body.banner
        assert bidderRequest.imp.first.video == secondImpProfile.body.video
        assert bidderRequest.imp.last.video == secondImpProfile.body.video
        assert bidderRequest.imp.first.video == thirdImpProfile.body.video
        assert bidderRequest.imp.banner == [firstImpProfile.body.banner]
    }

    def "PBS should include invalid or missing profiles into limit count"() {
        given: "Default bidRequest with request profiles"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileRequest = ProfileRequest.getProfile(accountId).tap {
            body = null
        }
        def impProfile = ProfileImp.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp.first.ext.prebid.profilesNames = [impProfile.name]
            it.ext.prebid.profilesNames = [invalidProfileRequest.name, PBSUtils.randomString]
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(invalidProfileRequest))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric shouldn't increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert !metrics[MISSING_PROFILE_METRIC]
        assert !metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)]

        and: "Bidder request should contain data from profiles"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }

        and: "Bidder imp should contain original data from request"
        assert bidderRequest.imp == bidRequest.imp
    }

    def "PBS should include data from storedBidResponses when it specified in profiles"() {
        given: "Default basic BidRequest with stored response"
        def accountId = PBSUtils.randomNumber as String
        def storedResponseId = PBSUtils.randomNumber
        def impProfile = ProfileImp.getProfile(accountId).tap {
            it.body.ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile])

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored bid response"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedBidResponse.seatbid[0].seat
        assert response.seatbid[0]?.bid?.size() == storedBidResponse.seatbid[0].bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == storedBidResponse.seatbid[0].bid[0].impid
        assert response.seatbid[0]?.bid[0]?.price == storedBidResponse.seatbid[0].bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedBidResponse.seatbid[0].bid[0].id

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should include data from storedAuctionResponse when it specified in profiles"() {
        given: "Default basic BidRequest with stored response"
        def accountId = PBSUtils.randomNumber as String
        def storedAuctionId = PBSUtils.randomNumber
        def impProfile = ProfileImp.getProfile(accountId).tap {
            it.body.ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedAuctionId)
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile])

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(BidRequest.defaultBidRequest)
        def storedResponse = new StoredResponse(responseId: storedAuctionId,
                storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored bid response"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedAuctionResponse.seat
        assert response.seatbid[0]?.bid?.size() == storedAuctionResponse.bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == storedAuctionResponse.bid[0].impid
        assert response.seatbid[0]?.bid[0]?.price == storedAuctionResponse.bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedAuctionResponse.bid[0].id

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should fail auction when fail-on-unknown-profile enabled and profile is missing"() {
        given: "PBS with profiles.fail-on-unknown config"
        def failOnUnknownProfilesConfig = new HashMap<>(PROFILES_CONFIG)
        failOnUnknownProfilesConfig["auction.profiles.fail-on-unknown"] = "true"
        def prebidServerService = pbsServiceFactory.getService(failOnUnknownProfilesConfig)

        and: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profilesNames = [PBSUtils.randomString]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS should fail auction"
        assert !response.seatbid.bid
        assert response.noBidResponse == NoBidResponse.INVALID_REQUEST

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(failOnUnknownProfilesConfig)
    }

    def "PBS should fail auction when fail-on-unknown-profile default and profile is missing"() {
        given: "PBS without profiles.fail-on-unknown config"
        def failOnUnknownProfilesConfig = new HashMap<>(PROFILES_CONFIG)
        failOnUnknownProfilesConfig.remove("auction.profiles.fail-on-unknown")
        def prebidServerService = pbsServiceFactory.getService(failOnUnknownProfilesConfig)

        and: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profilesNames = [PBSUtils.randomString]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS should fail auction"
        assert !response.seatbid.bid
        assert response.noBidResponse == NoBidResponse.INVALID_REQUEST

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(failOnUnknownProfilesConfig)
    }

    def "PBS should prioritise fail-on-unknown-profile from account over host config"() {
        given: "PBS with profiles.fail-on-unknown config"
        def failOnUnknownProfilesConfig = new HashMap<>(PROFILES_CONFIG)
        failOnUnknownProfilesConfig["auction.profiles.fail-on-unknown"] = "true"
        def prebidServerService = pbsServiceFactory.getService(failOnUnknownProfilesConfig)

        and: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profilesNames = [PBSUtils.randomString]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def accountAuctionConfig = new AccountAuctionConfig(profiles: profilesConfigs)
        def accountConfig = new AccountConfig(auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE, config: accountConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(prebidServerService)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert prebidServerService.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric should increments"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[MISSING_PROFILE_METRIC] == 1
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(failOnUnknownProfilesConfig)

        where:
        profilesConfigs << [
                new AccountProfilesConfigs(failOnUnknown: true),
                new AccountProfilesConfigs(failOnUnknownSnakeCase: true),
        ]
    }

    def "PBS should ignore inner request profiles when stored request profile contain link for another profile"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def innerRequestProfile = ProfileRequest.getProfile(accountId).tap {
            it.body.app = App.defaultApp
        }

        def requestProfile = ProfileRequest.getProfile(accountId).tap {
            it.body.ext.prebid.profilesNames = [innerRequestProfile.name]
        }
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(innerRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site == requestProfile.body.site
            it.device == requestProfile.body.device
        }

        and: "Bidder request shouldn't contain data from inner profile"
        assert !bidderRequest.app
    }

    def "PBS should ignore inner imp profiles when stored imp profile contain link for another profile"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def innerImpProfile = ProfileImp.getProfile(accountId, Imp.getDefaultImpression(VIDEO))
        def impProfile = ProfileImp.getProfile(accountId).tap {
            body.ext.prebid.profilesNames = [innerImpProfile.name]
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]).tap {
            imp.first.banner = null
        } as BidRequest

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profiles in database"
        profileImpDao.save(StoredProfileImp.getProfile(innerImpProfile))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        def bidderImp = bidder.getBidderRequest(bidRequest.id).imp.first
        assert bidderImp.banner == impProfile.body.banner

        and: "Bidder request imp shouldn't contain data from inner profile"
        assert bidderImp.video == impProfile.body.video
    }

    def "PBS shouldn't validate profiles and imp before margining"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ProfileImp.getProfile(accountId).tap {
            body.banner.weight = null
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]).tap {
            imp.first.banner.height = null
        } as BidRequest

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp.first == impProfile.body
    }

    def "PBS should ignore imp data from request profile when imp for profile not null"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ProfileRequest.getProfile(accountId,
                BidRequest.defaultBidRequest,
                PBSUtils.randomString,
                mergePrecedence)
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp == bidRequest.imp

        where:
        mergePrecedence << [REQUEST, PROFILE]
    }

    def "PBS should emit error and metrics when request profile called from imp level"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ProfileRequest.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profilesNames = [requestProfile.recordName]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [REJECT_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(REJECT_ERROR_MESSAGE)

        and: "Reject metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[REJECT_PROFILE_METRIC] == 1
        assert metrics[REJECT_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }
    }

    def "PBS should emit error and metrics when imp profile called from request level"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ProfileImp.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profilesNames = [requestProfile.recordName]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(requestProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(ERROR_MESSAGE)

        and: "Reject metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[REJECT_PROFILE_METRIC] == 1
        assert metrics[REJECT_ACCOUNT_PROFILE_METRIC] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }
    }

    def "PBS should emit error and metrics when request profile missing"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profilesNames = [PBSUtils.randomString]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_PROFILE_METRIC] == 1
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }
    }

    def "PBS should emit error and metrics when imp profile missing"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profilesNames = [PBSUtils.randomString]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        and: "Default account"
        def account = new Account(uuid: bidRequest.accountId, status: ACTIVE)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)


        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [MISSING_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(MISSING_ERROR_MESSAGE)

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_PROFILE_METRIC] == 1
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }
    }

    private static BidRequest getRequestWithProfiles(String accountId, List<Profile> profiles) {
        BidRequest.getDefaultBidRequest().tap {
            if (profiles.type.contains(ProfileType.IMP)) {
                imp = [new Imp(ext: new ImpExt(prebid: new ImpExtPrebid(profilesNames: profiles.findAll { it.type == ProfileType.IMP }*.name)))]
            }
            imp.first.ext.prebid.profilesNames = profiles.findAll { it.type == ProfileType.IMP }*.name
            it.ext.prebid.profilesNames = profiles.findAll { it.type == ProfileType.REQUEST }*.name
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }
    }
}
