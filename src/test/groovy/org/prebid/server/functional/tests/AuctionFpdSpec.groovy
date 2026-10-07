package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountBidderConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountSetting
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.MergeRule.CONCAT
import static org.prebid.server.functional.model.config.MergeRule.REPLACE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class AuctionFpdSpec extends BaseSpec {

    def "PBS should pass all FPD field to bidder request when FPD present in request"() {
        given: "Bid request with FPD fields"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = Site.rootFPDSite
            user = User.rootFPDUser
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain FPD field from request"
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
            it.site.content.id == bidRequest.site.content.id
            it.site.content.title == bidRequest.site.content.title
            it.site.content.data[0].id == bidRequest.site.content.data[0].id
            it.site.content.data[0].name == bidRequest.site.content.data[0].name
            it.site.publisher.id == bidRequest.site.publisher.id
            it.site.publisher.name == bidRequest.site.publisher.name
            it.site.publisher.domain == bidRequest.site.publisher.domain
            it.site.keywords == bidRequest.site.keywords
            it.site.mobile == bidRequest.site.mobile
            it.site.privacyPolicy == bidRequest.site.privacyPolicy
            it.site.ext.data.language == bidRequest.site.ext.data.language
            it.user.yob == bidRequest.user.yob
            it.user.gender == bidRequest.user.gender

            it.user.keywords == bidRequest.user.keywords
            it.user.geo.zip == bidRequest.user.geo.zip
            it.user.geo.country == bidRequest.user.geo.country
            it.user.ext.data.keywords == bidRequest.user.ext.data.keywords
            it.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
            it.user.ext.data.buyeruids == bidRequest.user.ext.data.buyeruids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        assert bidderRequest.imp.every { it.ext.rp == null }
    }

    def "PBS should populate FPD via bidder config when config.ortb2 is present"() {
        given: "BidRequest with FPD fields"
        def siteOrtb2Config = Site.configFPDSite
        def userOrtb2Config = User.configFPDUser
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = new Site(page: PBSUtils.randomString)
            user = new User(keywords: PBSUtils.randomString)
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: siteOrtb2Config, user: userOrtb2Config)))]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain FPD field from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.name == siteOrtb2Config.name
            it.site.domain == siteOrtb2Config.domain
            it.site.cat == siteOrtb2Config.cat
            it.site.sectionCat == siteOrtb2Config.sectionCat
            it.site.pageCat == siteOrtb2Config.pageCat
            it.site.page == siteOrtb2Config.page
            it.site.ref == siteOrtb2Config.ref
            it.site.search == siteOrtb2Config.search
            it.site.keywords == siteOrtb2Config.keywords
            it.site.ext.data.language == siteOrtb2Config.ext.data.language

            it.user.yob == userOrtb2Config.yob
            it.user.gender == userOrtb2Config.gender
            it.user.keywords == userOrtb2Config.keywords
            it.user.ext.data.keywords == userOrtb2Config.ext.data.keywords
            it.user.ext.data.buyeruid == userOrtb2Config.ext.data.buyeruid
            it.user.ext.data.buyeruids == userOrtb2Config.ext.data.buyeruids
        }

        and: "Bidder request shouldn't contain imp[0].ext.rp"
        assert bidderRequest.imp.every { it.ext.rp == null }
    }

    def "PBS should override FPD site/user from bidder config when config.ortb2 is present and merge strategy is default or replace"() {
        given: "BidRequest with FPD fields"
        def siteOrtb2Config = new Site(id: PBSUtils.randomNumber, keywords: PBSUtils.randomString, content: Content.FPDContent)
        def userOrtb2Config = new User(keywords: PBSUtils.randomString, data: [Data.defaultData], eids: [Eid.defaultEid])
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = new Site(id: PBSUtils.randomNumber, content: Content.FPDContent, keywords: PBSUtils.randomString)
            user = new User(data: [Data.defaultData], eids: [Eid.defaultEid], keywords: PBSUtils.randomString)
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: siteOrtb2Config, user: userOrtb2Config)))]
            setAccountId(accountId)
        }

        and: "Account in the DB"
        def accountBidderConfig = new AccountBidderConfig(arrayMerge: mergeStrategy)
        def accountSetting = new AccountSetting(bidderConfig: accountBidderConfig)
        def accountConfig = new AccountConfig(settings: accountSetting)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain FPD field from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            it.site.content == siteOrtb2Config.content
            it.site.keywords == siteOrtb2Config.keywords
            it.user == userOrtb2Config
        }

        and: "Bidder request shouldn't contain bidder config"
        assert !bidderRequest.ext.prebid.bidderConfig

        where:
        mergeStrategy << [null, REPLACE]
    }

    def "PBS should concatenated FPD array site/user fields from bidder config when config.ortb2 is present and merge strategy is concat"() {
        given: "BidRequest with FPD fields"
        def siteOrtb2Config = new Site(id: PBSUtils.randomNumber, content: Content.FPDContent, keywords: PBSUtils.randomString)
        def userOrtb2Config = new User(data: [Data.defaultData], eids: [Eid.defaultEid], keywords: PBSUtils.randomString)
        def siteRequest = new Site(id: PBSUtils.randomNumber, content: Content.FPDContent, keywords: PBSUtils.randomString)
        def userRequest = new User(data: [Data.defaultData], eids: [Eid.defaultEid], keywords: PBSUtils.randomString)
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            site = siteRequest
            user = userRequest
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: siteOrtb2Config, user: userOrtb2Config)))]
            setAccountId(accountId)
        }

        and: "Account in the DB"
        def accountBidderConfig = new AccountBidderConfig(arrayMerge: CONCAT)
        def accountSetting = new AccountSetting(bidderConfig: accountBidderConfig)
        def accountConfig = new AccountConfig(settings: accountSetting)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain concatenated FPD array fields from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.site.content.data.sort() == [siteOrtb2Config, siteRequest].content.data.flatten().sort()
        assert bidderRequest.user.data.sort() == [userOrtb2Config, userRequest].data.flatten().sort()
        assert bidderRequest.user.eids.sort() == [userOrtb2Config, userRequest].eids.flatten().sort()

        and: "Bidder request should contain overridden FPD object fields from request"
        verifyAll (bidRequest) {
            it.site.keywords == siteOrtb2Config.keywords
            it.user.keywords == userOrtb2Config.keywords
        }

        and: "Bidder request shouldn't contain bidder config"
        assert !bidderRequest.ext.prebid.bidderConfig
    }

    def "PBS should concatenated duplicates for FPD array site/user fields when config.ortb2 is present and merge strategy is concat"() {
        given: "BidRequest with FPD fields"
        def siteCat = [PBSUtils.randomString, PBSUtils.randomString]
        def siteContent = new Content(id: PBSUtils.randomString, data: [Data.defaultData, Data.defaultData])
        def userData = [Data.defaultData, Data.defaultData]
        def userKwarray = [PBSUtils.randomString, PBSUtils.randomString]
        def site = new Site(id: PBSUtils.randomNumber, cat: siteCat, content: siteContent)
        def user = new User(data: userData, kwarray: userKwarray)
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            it.site = site
            it.user = user
            it.ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: site, user: user)))]
            setAccountId(PBSUtils.randomNumber as String)
        }

        and: "Account in the DB"
        def accountBidderConfig = new AccountBidderConfig(arrayMerge: CONCAT)
        def accountSetting = new AccountSetting(bidderConfig: accountBidderConfig)
        def accountConfig = new AccountConfig(settings: accountSetting)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain concatenated deep duplicate FPD array fields from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.site.content.data.sort() == [site, site].content.data.flatten().sort()
        assert bidderRequest.user.data.sort() == [user, user].data.flatten().sort()

        and: "Bidder request should contain concatenated duplicate FPD fields from request"
        assert bidderRequest.site.cat.sort() == [site, site].cat.flatten().sort()
        assert bidderRequest.user.eids.sort() == [user, user].eids.flatten().sort()

        and: "Bidder request shouldn't contain bidder config"
        assert !bidderRequest.ext.prebid.bidderConfig
    }
}
