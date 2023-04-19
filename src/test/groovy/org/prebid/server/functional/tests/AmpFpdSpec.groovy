package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.amp.Targeting
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
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
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
            ampStoredRequest.site.sectioncat == site.sectioncat
            ampStoredRequest.site.pagecat == site.pagecat
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
            ampStoredRequest.site.privacypolicy == site.privacypolicy
            ampStoredRequest.site.ext.data.language == site.ext.data.language

            ampStoredRequest.user.yob == user.yob
            ampStoredRequest.user.gender == user.gender
            ampStoredRequest.user.keywords == user.keywords
            ampStoredRequest.user.geo.zip == user.geo.zip
            ampStoredRequest.user.geo.country == user.geo.country
            ampStoredRequest.user.ext.data.keywords == user.ext.data.keywords
            ampStoredRequest.user.ext.data.buyeruid == user.ext.data.buyeruid
            ampStoredRequest.user.ext.data.buyeruids == user.ext.data.buyeruids

            !imp[0].ext.rp
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
        assert ampStoredRequest.user.data[0].id == bidderRequest.user.data[0].id
        assert ampStoredRequest.user.data[0].name == bidderRequest.user.data[0].name
    }

    def "PBS should populate all FPD via targeting when targeting is present"() {
        given: "AMP request"
        def targeting = new Targeting().tap {
            site = Site.configFPDSite
            user = User.configFPDUser
            keywords = [PBSUtils.randomString]
            bidders = [GENERIC]
        }
        def encodeTargeting = HttpUtil.encodeUrl(encode(targeting))
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString, targeting: encodeTargeting)

        and: "Stored request with FPD fields"
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = new Site(page: PBSUtils.randomString)
            user = new User(keywords: PBSUtils.randomString)
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll(bidderRequest) {
            targeting.site.name == site.name
            targeting.site.domain == site.domain
            targeting.site.cat == site.cat
            targeting.site.sectioncat == site.sectioncat
            targeting.site.pagecat == site.pagecat
            targeting.site.page == site.page
            targeting.site.ref == site.ref
            targeting.site.search == site.search
            targeting.site.keywords == site.keywords
            targeting.site.ext.data.language == site.ext.data.language

            targeting.user.yob == user.yob
            targeting.user.gender == user.gender
            targeting.user.keywords == user.keywords
            targeting.user.ext.data.keywords == user.ext.data.keywords
            targeting.user.ext.data.buyeruid == user.ext.data.buyeruid
            targeting.user.ext.data.buyeruids == user.ext.data.buyeruids

            !imp[0].ext.rp
        }
    }

    def "PBS should emit error when targeting field is invalid"() {
        given: "AMP request with invalid targeting"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString, targeting: PBSUtils.randomString)

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
                "Error reading targeting json Unrecognized token '${ampRequest.targeting}': was expecting")
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

    def "PBS should take precedence target from request when stored request contain site/user"() {
        given: "AMP request"
        def targeting = new Targeting().tap {
            site = Site.configFPDSite
            user = User.configFPDUser
            keywords = [PBSUtils.randomString]
            bidders = [GENERIC]
        }
        def encodeTargeting = HttpUtil.encodeUrl(encode(targeting))
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString, targeting: encodeTargeting)

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
            targeting.site.name == site.name
            targeting.site.domain == site.domain
            targeting.site.cat == site.cat
            targeting.site.sectioncat == site.sectioncat
            targeting.site.pagecat == site.pagecat
            targeting.site.page == site.page
            targeting.site.ref == site.ref
            targeting.site.search == site.search
            targeting.site.keywords == site.keywords
            targeting.site.ext.data.language == site.ext.data.language

            targeting.user.yob == user.yob
            targeting.user.gender == user.gender
            targeting.user.keywords == user.keywords
            targeting.user.ext.data.keywords == user.ext.data.keywords
            targeting.user.ext.data.buyeruid == user.ext.data.buyeruid
            targeting.user.ext.data.buyeruids == user.ext.data.buyeruids

            !imp[0].ext.rp
        }
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
            ortb2.site.sectioncat == site.sectioncat
            ortb2.site.pagecat == site.pagecat
            ortb2.site.page == site.page
            ortb2.site.ref == site.ref
            ortb2.site.search == site.search
            ortb2.site.keywords == site.keywords
            ortb2.site.ext.data.language == site.ext.data.language

            ortb2.user.yob == user.yob
            ortb2.user.gender == user.gender
            ortb2.user.keywords == user.keywords
            ortb2.user.ext.data.keywords == user.ext.data.keywords
            ortb2.user.ext.data.buyeruid == user.ext.data.buyeruid
            ortb2.user.ext.data.buyeruids == user.ext.data.buyeruids

            !imp[0].ext.rp
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
                ampStoredRequest.site.sectioncat == site.sectioncat
                ampStoredRequest.site.pagecat == site.pagecat
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
                ampStoredRequest.site.privacypolicy == site.privacypolicy

                ampStoredRequest.user.yob == user.yob
                ampStoredRequest.user.gender == user.gender
                ampStoredRequest.user.keywords == user.keywords
                ampStoredRequest.user.geo.zip == user.geo.zip
                ampStoredRequest.user.geo.country == user.geo.country
                ampStoredRequest.user.ext.data.keywords == user.ext.data.keywords
                ampStoredRequest.user.ext.data.buyeruid == user.ext.data.buyeruid
                ampStoredRequest.user.ext.data.buyeruids == user.ext.data.buyeruids

                !imp[0].ext.rp
            }
        }
    }

    def "PBS should not send certain FPD data when bidder was not defined in bidders section"() {
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

        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.tap {
                data = new ExtRequestPrebidData(bidders: [GENERIC.value])
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
        def ortb2 = ampStoredRequest.ext.prebid.bidderConfig[0].config.ortb2

        verifyAll(bidderRequest) {
            ortb2.site.name == site.name
            ortb2.site.domain == site.domain
            ortb2.site.cat == site.cat
            ortb2.site.sectioncat == site.sectioncat
            ortb2.site.pagecat == site.pagecat
            ortb2.site.page == site.page
            ortb2.site.ref == site.ref
            ortb2.site.search == site.search
            ortb2.site.keywords == site.keywords
            ortb2.site.ext.data.language == site.ext.data.language

            ortb2.user.yob == user.yob
            ortb2.user.gender == user.gender
            ortb2.user.keywords == user.keywords
            ortb2.user.ext.data.keywords == user.ext.data.keywords
            ortb2.user.ext.data.buyeruid == user.ext.data.buyeruid
            ortb2.user.ext.data.buyeruids == user.ext.data.buyeruids

            !imp[0].ext.rp
        }
    }

    def "PBS should not send certain FPD data when allowed in bidder config and bidder was not defined in bidders section"() {
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

    def "PBS should merge unknown FPD to target when unknown FPD data present"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Stored request"
        def fpdGeo = Geo.FPDGeo
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config: new BidderConfig(ortb2:
                    new BidderConfigOrtb(user: new User(geo: fpdGeo))))]

        }

        and: "Save stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.user.ext.data.geo.country == fpdGeo.country
        assert bidderRequest.user.ext.data.geo.zip == fpdGeo.zip
        assert bidderRequest.user.geo.country == ampStoredRequest.user.geo.country
        assert bidderRequest.user.geo.zip == ampStoredRequest.user.geo.zip
    }

    def "PBS shouldn't pass gam adSlot to XAPI without leading slash dropped when adSlot specified"() {
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

        then: "Bidder request shouldn't contain certain FPD field from the stored request"
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

        then: "Bidder request shouldn't contain certain FPD field from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site.publisher.id == ampRequest.account
    }
}
