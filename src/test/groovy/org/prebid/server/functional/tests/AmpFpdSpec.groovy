package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.ExtRequestPrebidData
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.ImpExtContextDataAdServer
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class AmpFpdSpec extends BaseSpec {

    def "PBS should pass all FPD field to bidder request when FPD present in request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            ampStoredRequest.site.id == site.id
            ampStoredRequest.site.name == site.name
            ampStoredRequest.site.domain == site.domain
            ampStoredRequest.site.cat == site.cat
            ampStoredRequest.site.sectionCat == site.sectionCat
            ampStoredRequest.site.pageCat == site.pageCat
            ampStoredRequest.site.page == site.page
            ampStoredRequest.site.ref == site.ref
            ampStoredRequest.site.search == site.search
            ampStoredRequest.site.content.id == site.content.id
            ampStoredRequest.site.content.title == site.content.title
            ampStoredRequest.site.content.data[0].id == site.content.data[0].id
            ampStoredRequest.site.content.data[0].name == site.content.data[0].name
            ampStoredRequest.site.publisher.id == site.publisher.id
            ampStoredRequest.site.publisher.name == site.publisher.name
            ampStoredRequest.site.publisher.domain == site.publisher.domain
            ampStoredRequest.site.keywords == site.keywords
            ampStoredRequest.site.mobile == site.mobile
            ampStoredRequest.site.privacyPolicy == site.privacyPolicy
            ampStoredRequest.site.ext.data.language == site.ext.data.language

            ampStoredRequest.user.yob == user.yob
            ampStoredRequest.user.gender == user.gender
            ampStoredRequest.user.keywords == user.keywords
            ampStoredRequest.user.geo.zip == user.geo.zip
            ampStoredRequest.user.geo.country == user.geo.country
            ampStoredRequest.user.ext.data.keywords == user.ext.data.keywords
            ampStoredRequest.user.ext.data.buyerUid == user.ext.data.buyerUid
            ampStoredRequest.user.ext.data.buyerUids == user.ext.data.buyerUids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        bidderRequest.each {
            verifyAll(it) {
                !imp[0].ext.rp
            }
        }
    }

    def "PBS should pass FPD user.data field to bidder request when data present in request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            user = User.defaultUser.tap {
                data = [Data.defaultData]
            }
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD data field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert ampStoredRequest.user.data[0].id == bidderRequest.user.data[0].id
        assert ampStoredRequest.user.data[0].name == bidderRequest.user.data[0].name
    }

    def "PBS should emit error when targeting field is invalid"() {
        given: "AMP request with invalid targeting"
        def invalidTargeting = "InvalidTargeting"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString, targeting: invalidTargeting)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE)

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody.startsWith("Invalid request format: " +
                "Error reading targeting json Unrecognized token '$invalidTargeting': was expecting")
    }

    def "PBS shouldn't populate FPD field via targeting when targeting field is absent"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString, targeting: null)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = new Site(page: PBSUtils.randomString)
            user = new User(keywords: PBSUtils.randomString)
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert ampStoredRequest.site.page == bidderRequest.site.page
        assert ampStoredRequest.site.keywords == bidderRequest.site.keywords
    }

    def "PBS should populate FPD via bidder config when config.ortb2 is present"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = new Site(page: PBSUtils.randomString)
            user = new User(keywords: PBSUtils.randomString)
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: Site.configFPDSite, user: User.configFPDUser)))]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        def ortb2 = ampStoredRequest.ext.prebid.bidderConfig[0].config.ortb2

        verifyAll(bidderRequest) {
            ortb2.site.name == site.name
            ortb2.site.domain == site.domain
            ortb2.site.cat == site.cat
            ortb2.site.sectionCat == site.sectionCat
            ortb2.site.pageCat == site.pageCat
            ortb2.site.page == site.page
            ortb2.site.ref == site.ref
            ortb2.site.search == site.search
            ortb2.site.keywords == site.keywords
            ortb2.site.ext.data.language == site.ext.data.language

            ortb2.user.yob == user.yob
            ortb2.user.gender == user.gender
            ortb2.user.keywords == user.keywords
            ortb2.user.ext.data.keywords == user.ext.data.keywords
            ortb2.user.ext.data.buyerUid == user.ext.data.buyerUid
            ortb2.user.ext.data.buyerUids == user.ext.data.buyerUids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        bidderRequest.each {
            verifyAll(it) {
                !imp[0].ext.rp
            }
        }
    }

    def "PBS should override FPD site/user from bidder config when config.ortb2 is present"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC],
                    config: new BidderConfig(ortb2: new BidderConfigOrtb(
                            site: new Site(domain: PBSUtils.randomString),
                            user: new User(keywords: PBSUtils.randomString))))]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert ampStoredRequest.ext.prebid.bidderConfig[0].config.ortb2.site.domain == bidderRequest.site.domain
        assert ampStoredRequest.ext.prebid.bidderConfig[0].config.ortb2.user.keywords == bidderRequest.user.keywords

        and: "Bidder request shouldn't contain bidder config"
        assert !bidderRequest.ext.prebid.bidderConfig
    }

    def "PBS should populate FPD from root when bidder was defined in prebid data"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
            imp[0].ext.prebid.bidder.alias = new Generic()
            ext.prebid.tap {
                aliases = [(ALIAS.value): GENERIC]
                data = new ExtRequestPrebidData(bidders: [ALIAS.value, GENERIC.value])
            }
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequests(ampStoredRequest.id)
        bidderRequest.each {
            verifyAll(it) {
                ampStoredRequest.site.id == site.id
                ampStoredRequest.site.name == site.name
                ampStoredRequest.site.domain == site.domain
                ampStoredRequest.site.cat == site.cat
                ampStoredRequest.site.sectionCat == site.sectionCat
                ampStoredRequest.site.pageCat == site.pageCat
                ampStoredRequest.site.page == site.page
                ampStoredRequest.site.ref == site.ref
                ampStoredRequest.site.search == site.search
                ampStoredRequest.site.content.id == site.content.id
                ampStoredRequest.site.content.title == site.content.title
                ampStoredRequest.site.content.data[0].id == site.content.data[0].id
                ampStoredRequest.site.content.data[0].name == site.content.data[0].name
                ampStoredRequest.site.publisher.id == site.publisher.id
                ampStoredRequest.site.publisher.name == site.publisher.name
                ampStoredRequest.site.publisher.domain == site.publisher.domain
                ampStoredRequest.site.keywords == site.keywords
                ampStoredRequest.site.mobile == site.mobile
                ampStoredRequest.site.privacyPolicy == site.privacyPolicy

                ampStoredRequest.user.yob == user.yob
                ampStoredRequest.user.gender == user.gender
                ampStoredRequest.user.keywords == user.keywords
                ampStoredRequest.user.geo.zip == user.geo.zip
                ampStoredRequest.user.geo.country == user.geo.country
                ampStoredRequest.user.ext.data.keywords == user.ext.data.keywords
                ampStoredRequest.user.ext.data.buyerUid == user.ext.data.buyerUid
                ampStoredRequest.user.ext.data.buyerUids == user.ext.data.buyerUids
            }
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        bidderRequest.each {
            verifyAll(it) {
                !imp[0].ext.rp
            }
        }
    }

    def "PBS shouldn't send certain FPD data when bidder was not defined in bidders section"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
            ext.prebid.data = new ExtRequestPrebidData(bidders: [BOGUS.value])
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            !site.content.data
            !imp[0].ext.rp
            !user.ext
        }
    }

    def "PBS should send certain FPD data when allowed in bidder config and bidder was defined in bidders section"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Amp stored request with FPD data"
        def fpdSite = Site.rootFPDSite
        def fpdUser = User.rootFPDUser
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.tap {
                data = new ExtRequestPrebidData(bidders: [extRequestPrebidDataBidder])
                bidderConfig = [new ExtPrebidBidderConfig(bidders: [prebidBidderConfigBidder], config: new BidderConfig(
                        ortb2: new BidderConfigOrtb(site: fpdSite, user: fpdUser)))]
            }
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            it.site.name == fpdSite.name
            it.site.domain == fpdSite.domain
            it.site.cat == fpdSite.cat
            it.site.sectionCat == fpdSite.sectionCat
            it.site.pageCat == fpdSite.pageCat
            it.site.page == fpdSite.page
            it.site.ref == fpdSite.ref
            it.site.search == fpdSite.search
            it.site.keywords == fpdSite.keywords
            it.site.ext.data.language == fpdSite.ext.data.language

            it.user.yob == fpdUser.yob
            it.user.gender == fpdUser.gender
            it.user.keywords == fpdUser.keywords
            it.user.ext.data.keywords == fpdUser.ext.data.keywords
            it.user.ext.data.buyerUid == fpdUser.ext.data.buyerUid
            it.user.ext.data.buyerUids == fpdUser.ext.data.buyerUids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        bidderRequest.each {
            verifyAll(it) {
                !imp[0].ext.rp
            }
        }

        where:
        extRequestPrebidDataBidder | prebidBidderConfigBidder
        GENERIC.value              | GENERIC_CAMEL_CASE
        GENERIC_CAMEL_CASE.value   | GENERIC
    }

    def "PBS shouldn't send certain FPD data when allowed in bidder config and bidder was not defined in bidders section"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.tap {
                data = new ExtRequestPrebidData(bidders: [BOGUS.value])
                bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config: new BidderConfig(
                        ortb2: new BidderConfigOrtb(site: Site.configFPDSite, user: User.configFPDUser)))]
            }
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll(bidderRequest) {
            !bidderRequest.imp[0].ext.rp
            !bidderRequest.site.ext.data
            !bidderRequest.user
        }
    }

    def "PBS should ignore any not FPD data value in bidderconfig.config when merging values"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request"
        def fpdSite = Site.rootFPDSite
        def fpdUser = User.rootFPDUser
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = fpdSite
            user = fpdUser
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config: new BidderConfig(ortb2:
                    new BidderConfigOrtb(user: new User(geo: Geo.FPDGeo), site: new Site(publisher: new Publisher(name: PBSUtils.randomString)))))]
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            it.site.name == fpdSite.name
            it.site.domain == fpdSite.domain
            it.site.cat == fpdSite.cat
            it.site.sectionCat == fpdSite.sectionCat
            it.site.pageCat == fpdSite.pageCat
            it.site.page == fpdSite.page
            it.site.ref == fpdSite.ref
            it.site.search == fpdSite.search
            it.site.keywords == fpdSite.keywords
            it.site.ext.data.language == fpdSite.ext.data.language

            it.user.yob == fpdUser.yob
            it.user.gender == fpdUser.gender
            it.user.keywords == fpdUser.keywords
            it.user.ext.data.keywords == fpdUser.ext.data.keywords
            it.user.ext.data.buyerUid == fpdUser.ext.data.buyerUid
            it.user.ext.data.buyerUids == fpdUser.ext.data.buyerUids
        }

        and: "Should should ignore any non FPD data"
        verifyAll(bidderRequest) {
            !it.user.ext.data.geo
            !it.site.ext.data.publisher
        }
    }

    def "PBS shouldn't pass gam adSlot without leading slash dropped when adSlot specified"() {
        given: "AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            imp[0].ext.tap {
                context = new ImpExtContext(data: new ImpExtContextData(adServer: new ImpExtContextDataAdServer(
                        name: PBSUtils.randomString,
                        adSlot: "/$PBSUtils.randomNumber/$PBSUtils.randomString/$PBSUtils.randomString"))
                )
            }
            ext.prebid.data = new ExtRequestPrebidData(bidders: [GENERIC.value])
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain gpid and rp"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequest.imp[0].ext.gpid
        assert !bidderRequest.imp[0].ext.rp
    }

    def "PBS should overwrite site page when in amp request present curl"() {
        given: "AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain in site.page value from amp request body curl"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site.page == ampRequest.curl
    }

    def "PBS should overwrite site publisher id when in amp request present account"() {
        given: "AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Stored request"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain in site.publisher.id value from amp request body account"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site.publisher.id == ampRequest.account
    }
}
