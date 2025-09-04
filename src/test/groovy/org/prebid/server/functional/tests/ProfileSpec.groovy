package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountProfilesConfigs
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredProfileImp
import org.prebid.server.functional.model.db.StoredProfileRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.filesystem.FileSystemAccountsConfig
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtPrebid
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.profile.Profile
import org.prebid.server.functional.model.request.profile.ImpProfile
import org.prebid.server.functional.model.request.profile.ProfileMergePrecedence
import org.prebid.server.functional.model.request.profile.RequestProfile
import org.prebid.server.functional.model.request.profile.ProfileType
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.repository.dao.ProfileImpDao
import org.prebid.server.functional.repository.dao.ProfileRequestDao
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.images.builder.Transferable
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.profile.ProfileMergePrecedence.PROFILE
import static org.prebid.server.functional.model.request.profile.ProfileMergePrecedence.REQUEST
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class ProfileSpec extends BaseSpec {

    private static final String PROFILES_PATH = '/app/prebid-server/profiles'
    private static final String REQUESTS_PATH = '/app/prebid-server/requests'
    private static final String IMPS_PATH = '/app/prebid-server/imps'
    private static final String RESPONSES_PATH = '/app/prebid-server/responses'
    private static final String CATEGORIES_PATH = '/app/prebid-server/categories'
    private static final String SETTINGS_FILENAME = '/app/prebid-server/settings.yaml'
    private static final Integer LIMIT_HOST_PROFILE = 2
    private static final Integer ACCOUNT_ID_FILE_STORAGE = PBSUtils.randomNumber

    private static final Map<String, String> FILESYSTEM_CONFIG = [
            'settings.filesystem.settings-filename'   : SETTINGS_FILENAME,
            'settings.filesystem.profiles-dir'        : PROFILES_PATH,
            'settings.filesystem.stored-requests-dir' : REQUESTS_PATH,
            'settings.filesystem.stored-imps-dir'     : IMPS_PATH,
            'settings.filesystem.stored-responses-dir': RESPONSES_PATH,
            'settings.filesystem.categories-dir'      : CATEGORIES_PATH
    ]

    private static final Map<String, String> PROFILES_CONFIG = [
            'auction.profiles.fail-on-unknown': "false",
            'auction.profiles.limit'          : LIMIT_HOST_PROFILE.toString(),
            'settings.database.profiles-query': "SELECT accountId, profileId, profile, mergePrecedence, type FROM profiles " +
                    "WHERE profileId in (%REQUEST_ID_LIST%, %IMP_ID_LIST%)".toString()]

    private static final String LIMIT_ERROR_MESSAGE = 'Profiles exceeded the limit.'
    private static final String INVALID_REQUEST_PREFIX = 'Invalid request format: Error during processing profiles: '
    private static final String NO_IMP_PROFILE_MESSAGE = "No imp profiles for ids [%s] were found"
    private static final String NO_REQUEST_PROFILE_MESSAGE = "No request profiles for ids [%s] were found"
    private static final String NO_PROFILE_MESSAGE = "No profile found for id: %s"

    private static final String LIMIT_EXCEEDED_ACCOUNT_PROFILE_METRIC = "account.%s.profiles.limit_exceeded"
    private static final String MISSING_ACCOUNT_PROFILE_METRIC = "account.%s.profiles.missing"

    private static final ProfileImpDao profileImpDao = repository.profileImpDao
    private static final ProfileRequestDao profileRequestDao = repository.profileRequestDao

    private static PrebidServerContainer pbsContainer
    private static PrebidServerService pbsWithStoredProfiles
    private static RequestProfile fileRequestProfile
    private static RequestProfile fileRequestProfileWithEmptyMerge
    private static ImpProfile fileImpProfile
    private static ImpProfile fileImpProfileWithEmptyMerge

    def setupSpec() {
        pbsContainer = new PrebidServerContainer(FILESYSTEM_CONFIG + PROFILES_CONFIG)
        fileRequestProfile = RequestProfile.getProfile(ACCOUNT_ID_FILE_STORAGE.toString())
        fileImpProfile = ImpProfile.getProfile(ACCOUNT_ID_FILE_STORAGE.toString())
        pbsContainer.withCopyToContainer(Transferable.of(encode(fileRequestProfile)), "$PROFILES_PATH/${fileRequestProfile.fileName}")
        pbsContainer.withCopyToContainer(Transferable.of(encode(fileImpProfile)), "$PROFILES_PATH/${fileImpProfile.fileName}")
        fileRequestProfileWithEmptyMerge = RequestProfile.getProfile(ACCOUNT_ID_FILE_STORAGE.toString()).tap {
            mergePrecedence = null
        }
        fileImpProfileWithEmptyMerge = ImpProfile.getProfile(ACCOUNT_ID_FILE_STORAGE.toString()).tap {
            body.banner.tap {
                btype = [PBSUtils.randomNumber]
                format = [Format.randomFormat]
            }
            mergePrecedence = null
        }
        pbsContainer.withCopyToContainer(Transferable.of(encode(fileRequestProfileWithEmptyMerge)), "$PROFILES_PATH/${fileRequestProfileWithEmptyMerge.fileName}")
        pbsContainer.withCopyToContainer(Transferable.of(encode(fileImpProfileWithEmptyMerge)), "$PROFILES_PATH/${fileImpProfileWithEmptyMerge.fileName}")
        pbsContainer.withFolder(REQUESTS_PATH)
        pbsContainer.withFolder(IMPS_PATH)
        pbsContainer.withFolder(RESPONSES_PATH)
        pbsContainer.withFolder(CATEGORIES_PATH)
        def accountsConfig = new FileSystemAccountsConfig(accounts: [new AccountConfig(id: ACCOUNT_ID_FILE_STORAGE, status: ACTIVE)])
        pbsContainer.withCopyToContainer(Transferable.of(encodeYaml(accountsConfig)),
                SETTINGS_FILENAME)
        pbsContainer.start()
        pbsWithStoredProfiles = new PrebidServerService(pbsContainer)
    }

    def cleanupSpec() {
        pbsContainer.stop()
    }

    def "PBS should use profile for request when it exist in database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = RequestProfile.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == requestProfile.body.site.id
            it.site.name == requestProfile.body.site.name
            it.site.domain == requestProfile.body.site.domain
            it.site.cat == requestProfile.body.site.cat
            it.site.sectionCat == requestProfile.body.site.sectionCat
            it.site.pageCat == requestProfile.body.site.pageCat
            it.site.page == requestProfile.body.site.page
            it.site.ref == requestProfile.body.site.ref
            it.site.search == requestProfile.body.site.search
            it.site.keywords == requestProfile.body.site.keywords
            it.site.ext.data == requestProfile.body.site.ext.data

            it.device.didsha1 == requestProfile.body.device.didsha1
            it.device.didmd5 == requestProfile.body.device.didmd5
            it.device.dpidsha1 == requestProfile.body.device.dpidsha1
            it.device.ifa == requestProfile.body.device.ifa
            it.device.macsha1 == requestProfile.body.device.macsha1
            it.device.macmd5 == requestProfile.body.device.macmd5
            it.device.dpidmd5 == requestProfile.body.device.dpidmd5
        }
    }

    def "PBS should use imp profile for request when it exist in database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ImpProfile.getProfile(accountId)
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]).tap {
            it.imp.first.banner = null
        } as BidRequest

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id).imp) {
            it.id == [impProfile.body.id]
            it.banner == [impProfile.body.banner]
        }
    }

    def "PBS should use profile for request when it exist in filesystem"() {
        given: "Default bidRequest with request profile"
        def bidRequest = getRequestWithProfiles(ACCOUNT_ID_FILE_STORAGE.toString(), [fileRequestProfile])

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == fileRequestProfile.body.site.id
            it.site.name == fileRequestProfile.body.site.name
            it.site.domain == fileRequestProfile.body.site.domain
            it.site.cat == fileRequestProfile.body.site.cat
            it.site.sectionCat == fileRequestProfile.body.site.sectionCat
            it.site.pageCat == fileRequestProfile.body.site.pageCat
            it.site.page == fileRequestProfile.body.site.page
            it.site.ref == fileRequestProfile.body.site.ref
            it.site.search == fileRequestProfile.body.site.search
            it.site.keywords == fileRequestProfile.body.site.keywords
            it.site.ext.data == fileRequestProfile.body.site.ext.data

            it.device.didsha1 == fileRequestProfile.body.device.didsha1
            it.device.didmd5 == fileRequestProfile.body.device.didmd5
            it.device.dpidsha1 == fileRequestProfile.body.device.dpidsha1
            it.device.ifa == fileRequestProfile.body.device.ifa
            it.device.macsha1 == fileRequestProfile.body.device.macsha1
            it.device.macmd5 == fileRequestProfile.body.device.macmd5
            it.device.dpidmd5 == fileRequestProfile.body.device.dpidmd5
        }
    }

    def "PBS should use imp profile for request when it exist in filesystem"() {
        given: "Default bidRequest with request profile"
        def bidRequest = getRequestWithProfiles(ACCOUNT_ID_FILE_STORAGE.toString(), [fileImpProfile]).tap {
            it.imp.first.banner = null
        } as BidRequest

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id).imp) {
            it.id == [fileImpProfile.body.id]
            it.banner == [fileImpProfile.body.banner]
        }
    }

    def "PBS should set merge strategy to default profile without error for request profile when merge strategy is empty in database"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = RequestProfile.getProfile(accountId).tap {
            it.mergePrecedence = null
        }
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile]).tap {
            it.site = Site.configFPDSite
        } as BidRequest

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from original request when data is present"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords
            it.site.ext.data == bidRequest.site.ext.data
        }

        and: "Bidder request should contain data from profile when data is empty"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.device.didsha1 == requestProfile.body.device.didsha1
            it.device.didmd5 == requestProfile.body.device.didmd5
            it.device.dpidsha1 == requestProfile.body.device.dpidsha1
            it.device.ifa == requestProfile.body.device.ifa
            it.device.macsha1 == requestProfile.body.device.macsha1
            it.device.macmd5 == requestProfile.body.device.macmd5
            it.device.dpidmd5 == requestProfile.body.device.dpidmd5
        }
    }

    def "PBS should set merge strategy to default profile without error for request profile when merge strategy is empty in filesystem"() {
        given: "Default bidRequest with request profile"
        def bidRequest = getRequestWithProfiles(ACCOUNT_ID_FILE_STORAGE.toString(), [fileRequestProfileWithEmptyMerge]).tap {
            it.site = Site.configFPDSite
        } as BidRequest

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from original request when data is present"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords
            it.site.ext.data == bidRequest.site.ext.data
        }

        and: "Bidder request should contain data from original request when data is empty"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.device.didsha1 == fileRequestProfileWithEmptyMerge.body.device.didsha1
            it.device.didmd5 == fileRequestProfileWithEmptyMerge.body.device.didmd5
            it.device.dpidsha1 == fileRequestProfileWithEmptyMerge.body.device.dpidsha1
            it.device.ifa == fileRequestProfileWithEmptyMerge.body.device.ifa
            it.device.macsha1 == fileRequestProfileWithEmptyMerge.body.device.macsha1
            it.device.macmd5 == fileRequestProfileWithEmptyMerge.body.device.macmd5
            it.device.dpidmd5 == fileRequestProfileWithEmptyMerge.body.device.dpidmd5
        }
    }

    def "PBS should set merge strategy to default profile without error for imp profile when merge strategy is empty in database"() {
        given: "Default bidRequest with imp profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ImpProfile.getProfile(accountId).tap {
            it.mergePrecedence = null
            body.banner.tap {
                btype = [PBSUtils.randomNumber]
                format = [Format.randomFormat]
            }
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile])

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile when data is present"
        def bidderImpBanner = bidder.getBidderRequest(bidRequest.id).imp.banner.first
        assert bidderImpBanner.format == bidRequest.imp.first.banner.format

        and: "Bidder request should contain data from profile when data is empty"
        assert bidderImpBanner.btype == impProfile.body.banner.btype
    }

    def "PBS should set merge strategy to default profile without error for imp profile when merge strategy is empty in filesystem"() {
        given: "Default bidRequest with imp profile"
        def bidRequest = getRequestWithProfiles(ACCOUNT_ID_FILE_STORAGE.toString(), [fileImpProfileWithEmptyMerge])

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imp should contain data from profile when data is present"
        def bidderImpBanner = bidder.getBidderRequest(bidRequest.id).imp.banner.first
        assert bidderImpBanner.format == bidRequest.imp.first.banner.format

        and: "Bidder request should contain data from profile when data is empty"
        assert bidderImpBanner.btype == fileImpProfileWithEmptyMerge.body.banner.btype
    }

    def "PBS should merge latest-specified profile when there merge conflict and different merge precedence present"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        firstProfile.accountId = accountId
        secondProfile.accountId = accountId
        def bidRequest = getRequestWithProfiles(accountId, [firstProfile, secondProfile]).tap {
            it.site = Site.configFPDSite
            it.device = Device.default
        } as BidRequest

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profiles"
        def mergedRequest = [firstProfile, secondProfile].find { it.mergePrecedence == PROFILE }.body
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == mergedRequest.site.id
            it.site.name == mergedRequest.site.name
            it.site.domain == mergedRequest.site.domain
            it.site.cat == mergedRequest.site.cat
            it.site.sectionCat == mergedRequest.site.sectionCat
            it.site.pageCat == mergedRequest.site.pageCat
            it.site.page == mergedRequest.site.page
            it.site.ref == mergedRequest.site.ref
            it.site.search == mergedRequest.site.search
            it.site.keywords == mergedRequest.site.keywords
            it.site.ext.data == mergedRequest.site.ext.data

            it.device.didsha1 == mergedRequest.device.didsha1
            it.device.didmd5 == mergedRequest.device.didmd5
            it.device.dpidsha1 == mergedRequest.device.dpidsha1
            it.device.ifa == mergedRequest.device.ifa
            it.device.macsha1 == mergedRequest.device.macsha1
            it.device.macmd5 == mergedRequest.device.macmd5
            it.device.dpidmd5 == mergedRequest.device.dpidmd5
        }

        where:
        firstProfile                                                  | secondProfile
        RequestProfile.getProfile().tap { mergePrecedence = REQUEST } | RequestProfile.getProfile()
        RequestProfile.getProfile()                                   | RequestProfile.getProfile().tap { mergePrecedence = REQUEST }
    }

    def "PBS should merge first-specified profile with request merge precedence when there merge conflict"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def firstRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.device = Device.default
            it.body.site = Site.rootFPDSite
            it.mergePrecedence = REQUEST
        }
        def secondRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.device = Device.default
            it.body.site = Site.rootFPDSite
            it.mergePrecedence = REQUEST
        }
        def bidRequest = getRequestWithProfiles(accountId, [firstRequestProfile, secondRequestProfile])

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondRequestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == firstRequestProfile.body.site.id
            it.site.name == firstRequestProfile.body.site.name
            it.site.domain == firstRequestProfile.body.site.domain
            it.site.cat == firstRequestProfile.body.site.cat
            it.site.sectionCat == firstRequestProfile.body.site.sectionCat
            it.site.pageCat == firstRequestProfile.body.site.pageCat
            it.site.ref == firstRequestProfile.body.site.ref
            it.site.search == firstRequestProfile.body.site.search
            it.site.keywords == firstRequestProfile.body.site.keywords
            it.site.ext.data == firstRequestProfile.body.site.ext.data

            it.device.didsha1 == firstRequestProfile.body.device.didsha1
            it.device.didmd5 == firstRequestProfile.body.device.didmd5
            it.device.dpidsha1 == firstRequestProfile.body.device.dpidsha1
            it.device.ifa == firstRequestProfile.body.device.ifa
            it.device.macsha1 == firstRequestProfile.body.device.macsha1
            it.device.macmd5 == firstRequestProfile.body.device.macmd5
            it.device.dpidmd5 == firstRequestProfile.body.device.dpidmd5
        }
    }

    def "PBS should merge latest-specified profile with profile merge precedence when there merge conflict"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def firstRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.device = Device.default
            it.body.site = Site.rootFPDSite
        }
        def secondRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.device = Device.default
            it.body.site = Site.rootFPDSite
        }
        def bidRequest = getRequestWithProfiles(accountId, [firstRequestProfile, secondRequestProfile])

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(firstRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(secondRequestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == secondRequestProfile.body.site.id
            it.site.name == secondRequestProfile.body.site.name
            it.site.domain == secondRequestProfile.body.site.domain
            it.site.cat == secondRequestProfile.body.site.cat
            it.site.sectionCat == secondRequestProfile.body.site.sectionCat
            it.site.pageCat == secondRequestProfile.body.site.pageCat
            it.site.page == secondRequestProfile.body.site.page
            it.site.ref == secondRequestProfile.body.site.ref
            it.site.search == secondRequestProfile.body.site.search
            it.site.keywords == secondRequestProfile.body.site.keywords
            it.site.ext.data == secondRequestProfile.body.site.ext.data

            it.device.didsha1 == secondRequestProfile.body.device.didsha1
            it.device.didmd5 == secondRequestProfile.body.device.didmd5
            it.device.dpidsha1 == secondRequestProfile.body.device.dpidsha1
            it.device.ifa == secondRequestProfile.body.device.ifa
            it.device.macsha1 == secondRequestProfile.body.device.macsha1
            it.device.macmd5 == secondRequestProfile.body.device.macmd5
            it.device.dpidmd5 == secondRequestProfile.body.device.dpidmd5
        }
    }

    def "PBS should prioritise profile for request and emit warning when request is overloaded by profiles"() {
        given: "Default bidRequest with profiles"
        def accountId = PBSUtils.randomNumber as String
        def profileSite = Site.rootFPDSite
        def profileDevice = Device.default
        def firstRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.site = profileSite
            it.body.device = null
        }
        def secondRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.site = null
            it.body.device = profileDevice
        }
        def impProfile = ImpProfile.getProfile(accountId, Imp.getDefaultImpression(VIDEO))
        def bidRequest = getRequestWithProfiles(accountId, [impProfile, firstRequestProfile, secondRequestProfile])

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
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [LIMIT_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[LIMIT_EXCEEDED_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profile"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.id == profileSite.id
            it.site.name == profileSite.name
            it.site.domain == profileSite.domain
            it.site.cat == profileSite.cat
            it.site.sectionCat == profileSite.sectionCat
            it.site.pageCat == profileSite.pageCat
            it.site.page == profileSite.page
            it.site.ref == profileSite.ref
            it.site.search == profileSite.search
            it.site.keywords == profileSite.keywords
            it.site.ext.data == profileSite.ext.data

            it.device.didsha1 == profileDevice.didsha1
            it.device.didmd5 == profileDevice.didmd5
            it.device.dpidsha1 == profileDevice.dpidsha1
            it.device.ifa == profileDevice.ifa
            it.device.macsha1 == profileDevice.macsha1
            it.device.macmd5 == profileDevice.macmd5
            it.device.dpidmd5 == profileDevice.dpidmd5
        }

        and: "Bidder imp should contain original data from request"
        assert verifyAll(bidderRequest.imp) {
            it.banner == bidRequest.imp.banner
            it.video == [null]
        }
    }

    def "PBS should be able override profile limit by account config and use remaining limits for each imp separately"() {
        given: "BidRequest with profiles"
        def accountId = PBSUtils.randomNumber as String
        def profileSite = Site.defaultSite
        def profileDevice = Device.default
        def firstRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.device = null
            it.body.site = profileSite
        }
        def secondRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.site = null
            it.body.device = profileDevice
        }
        def firstImp = Imp.defaultImpression.tap {
            it.banner.btype = [PBSUtils.randomNumber]
        }
        def secondImp = Imp.defaultImpression.tap {
            it.banner.battr = [PBSUtils.randomNumber]
        }
        def thirdImp = Imp.defaultImpression.tap {
            it.banner.mimes = [PBSUtils.randomString]
        }
        def firstImpProfile = ImpProfile.getProfile(accountId, firstImp)
        def secondImpProfile = ImpProfile.getProfile(accountId, secondImp)
        def thirdImpProfile = ImpProfile.getProfile(accountId, thirdImp)
        def bidRequest = getRequestWithProfiles(accountId, [firstImpProfile, secondImpProfile, firstRequestProfile, secondRequestProfile]).tap {
            imp << new Imp(ext: new ImpExt(prebid: new ImpExtPrebid(profileNames: [secondImpProfile, thirdImpProfile].id)))
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

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Missing metric shouldn't increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert !metrics[LIMIT_EXCEEDED_ACCOUNT_PROFILE_METRIC.formatted(accountId)]

        and: "Bidder request should contain data from profiles"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.id == profileSite.id
            it.site.name == profileSite.name
            it.site.domain == profileSite.domain
            it.site.cat == profileSite.cat
            it.site.sectionCat == profileSite.sectionCat
            it.site.pageCat == profileSite.pageCat
            it.site.page == profileSite.page
            it.site.ref == profileSite.ref
            it.site.search == profileSite.search
            it.site.keywords == profileSite.keywords

            it.device.didsha1 == profileDevice.didsha1
            it.device.didmd5 == profileDevice.didmd5
            it.device.dpidsha1 == profileDevice.dpidsha1
            it.device.ifa == profileDevice.ifa
            it.device.macsha1 == profileDevice.macsha1
            it.device.macmd5 == profileDevice.macmd5
            it.device.dpidmd5 == profileDevice.dpidmd5
        }

        and: "Bidder imp should contain data from specified profiles"
        def firstBidderImpBanner = bidderRequest.imp.first.banner
        verifyAll(firstBidderImpBanner) {
            it.btype == firstImpProfile.body.banner.btype
            it.battr == secondImpProfile.body.banner.battr
        }

        and: "Ignore data from unspecified profiles"
        assert !firstBidderImpBanner.mimes

        and: "Bidder imp should contain data from specified profiles"
        def secondBidderImpBanner = bidderRequest.imp.last.banner
        verifyAll(secondBidderImpBanner) {
            it.battr == secondImpProfile.body.banner.battr
            it.mimes == thirdImpProfile.body.banner.mimes
        }

        and: "Ignore data from unspecified profiles"
        assert !secondBidderImpBanner.btype
    }

    def "PBS should count invalid or missing profiles towards the limit"() {
        given: "Default bidRequest with request profiles"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileRequest = RequestProfile.getProfile(accountId).tap {
            it.body = null
        }
        def impProfile = ImpProfile.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.tap {
                it.banner.format = [Format.randomFormat]
                it.ext.prebid.profileNames = [impProfile.id]
            }
            it.ext.prebid.profileNames = [invalidProfileRequest.id, PBSUtils.randomString]
            it.site = Site.configFPDSite
            it.device = Device.default
            setAccountId(accountId)
        }

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(invalidProfileRequest))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.message.contains(LIMIT_ERROR_MESSAGE)

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[LIMIT_EXCEEDED_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords
            it.site.ext.data == bidRequest.site.ext.data

            it.device.didsha1 == bidRequest.device.didsha1
            it.device.didmd5 == bidRequest.device.didmd5
            it.device.dpidsha1 == bidRequest.device.dpidsha1
            it.device.ifa == bidRequest.device.ifa
            it.device.macsha1 == bidRequest.device.macsha1
            it.device.macmd5 == bidRequest.device.macmd5
            it.device.dpidmd5 == bidRequest.device.dpidmd5
        }

        and: "Bidder request imp should contain data from request"
        assert bidder.getBidderRequest(bidRequest.id).imp.banner == bidRequest.imp.banner
    }

    def "PBS should include data from storedBidResponses when it specified in profiles"() {
        given: "Default BidRequest with profile"
        def accountId = PBSUtils.randomNumber as String
        def storedResponseId = PBSUtils.randomNumber
        def impProfile = ImpProfile.getProfile(accountId).tap {
            it.body.id = null
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
        given: "Default basic BidRequest with profile"
        def accountId = PBSUtils.randomNumber as String
        def storedAuctionId = PBSUtils.randomNumber
        def impProfile = ImpProfile.getProfile(accountId).tap {
            it.body.id = null
            it.body.ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedAuctionId)
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile])

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
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
        def prebidServerService = pbsServiceFactory.getService(PROFILES_CONFIG +
                ['auction.profiles.fail-on-unknown': 'true'])

        and: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [invalidProfileId]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }


        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to invalid profile"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == INVALID_REQUEST_PREFIX + NO_IMP_PROFILE_MESSAGE.formatted(invalidProfileId)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(PROFILES_CONFIG + ['auction.profiles.fail-on-unknown': 'true'])
    }

    def "PBS should fail auction when fail-on-unknown-profile default and profile is missing"() {
        given: "PBS without profiles.fail-on-unknown config"
        def prebidServerService = pbsServiceFactory.getService(PROFILES_CONFIG + ['auction.profiles.fail-on-unknown': null])

        and: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [invalidProfileId]
            it.site = new Site()
            it.device = null
            setAccountId(accountId)
        }

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to invalid profile"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == INVALID_REQUEST_PREFIX + NO_IMP_PROFILE_MESSAGE.formatted(invalidProfileId)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(PROFILES_CONFIG + ['auction.profiles.fail-on-unknown': null])
    }

    def "PBS should prioritise fail-on-unknown-profile from account over host config"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [invalidProfileId]
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
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to invalid profile"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == INVALID_REQUEST_PREFIX + NO_IMP_PROFILE_MESSAGE.formatted(invalidProfileId)

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        where:
        profilesConfigs << [
                new AccountProfilesConfigs(failOnUnknown: true),
                new AccountProfilesConfigs(failOnUnknownSnakeCase: true),
        ]
    }

    def "PBS should ignore inner request profiles when stored request profile contain link for another profile"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def innerRequestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.app = App.defaultApp
        }

        def requestProfile = RequestProfile.getProfile(accountId).tap {
            it.body.ext.prebid.profileNames = [innerRequestProfile.id]
        }
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile]).tap {
            it.site = Site.configFPDSite
            it.device = Device.default
        } as BidRequest

        and: "Default profiles in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(innerRequestProfile))
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.id == requestProfile.body.site.id
            it.site.name == requestProfile.body.site.name
            it.site.domain == requestProfile.body.site.domain
            it.site.cat == requestProfile.body.site.cat
            it.site.sectionCat == requestProfile.body.site.sectionCat
            it.site.pageCat == requestProfile.body.site.pageCat
            it.site.page == requestProfile.body.site.page
            it.site.ref == requestProfile.body.site.ref
            it.site.search == requestProfile.body.site.search
            it.site.keywords == requestProfile.body.site.keywords
            it.site.ext.data == requestProfile.body.site.ext.data

            it.device.didsha1 == requestProfile.body.device.didsha1
            it.device.didmd5 == requestProfile.body.device.didmd5
            it.device.dpidsha1 == requestProfile.body.device.dpidsha1
            it.device.ifa == requestProfile.body.device.ifa
            it.device.macsha1 == requestProfile.body.device.macsha1
            it.device.macmd5 == requestProfile.body.device.macmd5
            it.device.dpidmd5 == requestProfile.body.device.dpidmd5
        }

        and: "Bidder request shouldn't contain data from inner profile"
        assert !bidderRequest.app
    }

    def "PBS should ignore inner imp profiles when stored imp profile contain link for another profile"() {
        given: "Default bidRequest with imp profile"
        def accountId = PBSUtils.randomNumber as String
        def innerImpProfile = ImpProfile.getProfile(accountId, Imp.getDefaultImpression(VIDEO))
        def impProfile = ImpProfile.getProfile(accountId).tap {
            it.body.ext.prebid.profileNames = [innerImpProfile.id]
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]).tap {
            it.imp.first.banner = null
        } as BidRequest

        and: "Default profiles in database"
        profileImpDao.save(StoredProfileImp.getProfile(innerImpProfile))
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
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
        def height = PBSUtils.randomNumber
        def impProfile = ImpProfile.getProfile(accountId).tap {
            it.body.banner.format.first.weight = null
            it.body.banner.format.first.height = height
        }
        def bidRequest = getRequestWithProfiles(accountId, [impProfile]) as BidRequest

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to invalid request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: request.imp[0].banner.format[0] must define a valid "h" and "w" properties'
    }

    def "PBS shouldn't emit error or warnings when bidRequest contains multiple imps with same profile"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def imp = Imp.defaultImpression.tap {
            it.banner.format = [Format.randomFormat]
        }
        def impProfile = ImpProfile.getProfile(accountId, imp)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            addImp(Imp.getDefaultImpression())
            setAccountId(accountId)
        } as BidRequest
        bidRequest.imp.each {
            it.ext.prebid.profileNames = [impProfile.id]
        }

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request imps should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp.first.banner == impProfile.body.banner
        assert bidder.getBidderRequest(bidRequest.id).imp.last.banner == impProfile.body.banner
    }

    def "PBS should ignore imp data from request profile when imp for profile not null"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequestProfile = BidRequest.defaultBidRequest.tap {
            it.id = null
            it.imp.first.banner.format = [Format.randomFormat]
        }
        def requestProfile = RequestProfile.getProfile(accountId,
                bidRequestProfile,
                PBSUtils.randomString,
                mergePrecedence)
        def bidRequest = getRequestWithProfiles(accountId, [requestProfile])

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain data from profile"
        assert bidder.getBidderRequest(bidRequest.id).imp.banner == bidRequest.imp.banner

        where:
        mergePrecedence << [REQUEST, PROFILE]
    }

    @PendingFeature
    def "PBS should add error and metrics when imp name is invalid"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def impProfile = ImpProfile.getProfile(accountId, Imp.defaultImpression, invalidProfileName)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [impProfile.id]
            setAccountId(accountId)
        }

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(impProfile))

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [LIMIT_ERROR_MESSAGE]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "PBS log should contain error"
        assert pbsWithStoredProfiles.isContainLogsByValue(LIMIT_ERROR_MESSAGE)

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[LIMIT_EXCEEDED_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from original request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site == bidRequest.site
            it.device == bidRequest.device
        }

        where:
        invalidProfileName << [PBSUtils.randomSpecialChars, PBSUtils.randomStringWithSpecials]
    }

    def "PBS should emit error and metrics when request profile called from imp level"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = RequestProfile.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [requestProfile.id]
            it.site = Site.getRootFPDSite()
            it.device = Device.getDefault()
            setAccountId(accountId)
        }

        and: "Default profile in database"
        profileRequestDao.save(StoredProfileRequest.getProfile(requestProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_PROFILE_MESSAGE.formatted(requestProfile.id)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords

            it.device.didsha1 == bidRequest.device.didsha1
            it.device.didmd5 == bidRequest.device.didmd5
            it.device.dpidsha1 == bidRequest.device.dpidsha1
            it.device.ifa == bidRequest.device.ifa
            it.device.macsha1 == bidRequest.device.macsha1
            it.device.macmd5 == bidRequest.device.macmd5
            it.device.dpidmd5 == bidRequest.device.dpidmd5
        }
    }

    def "PBS should emit error and metrics when imp profile called from request level"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def requestProfile = ImpProfile.getProfile(accountId)
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profileNames = [requestProfile.id]
            it.site = Site.getRootFPDSite()
            it.device = Device.getDefault()
            setAccountId(accountId)
        }

        and: "Default profile in database"
        profileImpDao.save(StoredProfileImp.getProfile(requestProfile))

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_PROFILE_MESSAGE.formatted(requestProfile.id)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords

            it.device.didsha1 == bidRequest.device.didsha1
            it.device.didmd5 == bidRequest.device.didmd5
            it.device.dpidsha1 == bidRequest.device.dpidsha1
            it.device.ifa == bidRequest.device.ifa
            it.device.macsha1 == bidRequest.device.macsha1
            it.device.macmd5 == bidRequest.device.macmd5
            it.device.dpidmd5 == bidRequest.device.dpidmd5
        }
    }

    def "PBS should emit error and metrics when imp profile missing"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [invalidProfileId]
            setAccountId(accountId)
        }

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_IMP_PROFILE_MESSAGE.formatted(invalidProfileId)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request imp should contain data from original imp"
        assert bidder.getBidderRequest(bidRequest.id).imp.banner == bidRequest.imp.banner
    }

    def "PBS should emit error and metrics when request profile missing"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profileNames = [invalidProfileId]
            it.site = Site.getRootFPDSite()
            it.device = Device.getDefault()
            setAccountId(accountId)
        }

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_REQUEST_PROFILE_MESSAGE.formatted(invalidProfileId)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords

            it.device.didsha1 == bidRequest.device.didsha1
            it.device.didmd5 == bidRequest.device.didmd5
            it.device.dpidsha1 == bidRequest.device.dpidsha1
            it.device.ifa == bidRequest.device.ifa
            it.device.macsha1 == bidRequest.device.macsha1
            it.device.macmd5 == bidRequest.device.macmd5
            it.device.dpidmd5 == bidRequest.device.dpidmd5
        }
    }

    def "PBS should emit error and metrics when imp profile have invalid data"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.imp.first.ext.prebid.profileNames = [invalidProfile.id]
            setAccountId(accountId)
        }

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_IMP_PROFILE_MESSAGE.formatted(invalidProfile.id)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request imp should contain data from original imp"
        assert bidder.getBidderRequest(bidRequest.id).imp.banner == bidRequest.imp.banner

        where:
        invalidProfile << [
                ImpProfile.getProfile().tap { it.type = ProfileType.EMPTY},
                ImpProfile.getProfile().tap { it.type = ProfileType.UNKNOWN},
                ImpProfile.getProfile().tap { it.mergePrecedence = ProfileMergePrecedence.EMPTY},
                ImpProfile.getProfile().tap { it.mergePrecedence = ProfileMergePrecedence.UNKNOWN},
        ]
    }

    def "PBS should emit error and metrics when request profile have invalid data"() {
        given: "Default bidRequest with request profile"
        def accountId = PBSUtils.randomNumber as String
        def invalidProfileId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            it.ext.prebid.profileNames = [invalidProfileId]
            it.site = Site.getRootFPDSite()
            it.device = Device.getDefault()
            setAccountId(accountId)
        }

        and: "Flash metrics"
        flushMetrics(pbsWithStoredProfiles)

        when: "PBS processes auction request"
        def response = pbsWithStoredProfiles.sendAuctionRequest(bidRequest)

        then: "PBS should emit proper warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message == [NO_REQUEST_PROFILE_MESSAGE.formatted(invalidProfileId)]

        and: "Response should contain error"
        assert !response.ext?.errors

        and: "Missing metric should increments"
        def metrics = pbsWithStoredProfiles.sendCollectedMetricsRequest()
        assert metrics[MISSING_ACCOUNT_PROFILE_METRIC.formatted(accountId)] == 1

        and: "Bidder request should contain data from profile"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            it.site.id == bidRequest.site.id
            it.site.name == bidRequest.site.name
            it.site.domain == bidRequest.site.domain
            it.site.cat == bidRequest.site.cat
            it.site.sectionCat == bidRequest.site.sectionCat
            it.site.pageCat == bidRequest.site.pageCat
            it.site.page == bidRequest.site.page
            it.site.ref == bidRequest.site.ref
            it.site.search == bidRequest.site.search
            it.site.keywords == bidRequest.site.keywords

            it.device.didsha1 == bidRequest.device.didsha1
            it.device.didmd5 == bidRequest.device.didmd5
            it.device.dpidsha1 == bidRequest.device.dpidsha1
            it.device.ifa == bidRequest.device.ifa
            it.device.macsha1 == bidRequest.device.macsha1
            it.device.macmd5 == bidRequest.device.macmd5
            it.device.dpidmd5 == bidRequest.device.dpidmd5
        }

        where:
        invalidProfile << [
                RequestProfile.getProfile().tap { it.type = ProfileType.EMPTY},
                RequestProfile.getProfile().tap { it.type = ProfileType.UNKNOWN},
                RequestProfile.getProfile().tap { it.mergePrecedence = ProfileMergePrecedence.EMPTY},
                RequestProfile.getProfile().tap { it.mergePrecedence = ProfileMergePrecedence.UNKNOWN},
        ]
    }

    private static BidRequest getRequestWithProfiles(String accountId, List<Profile> profiles) {
        BidRequest.getDefaultBidRequest().tap {
            if (profiles.type.contains(ProfileType.IMP)) {
                it.imp.first.ext.prebid.profileNames = profiles.findAll { it.type == ProfileType.IMP }*.id
            }
            it.imp.first.ext.prebid.profileNames = profiles.findAll { it.type == ProfileType.IMP }*.id
            it.ext.prebid.profileNames = profiles.findAll { it.type == ProfileType.REQUEST }*.id
            setAccountId(accountId)
        }
    }
}
