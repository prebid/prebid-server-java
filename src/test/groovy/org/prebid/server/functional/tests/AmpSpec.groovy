package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ConsentedProvidersSettings
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AmpSpec extends BaseSpec {

    def "PBS should return version in response header for #description"() {
        given: "Default AmpRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequestRaw(ampRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == "pbs-java/$PBS_VERSION"

        where:
        ampRequest                   || description
        AmpRequest.defaultAmpRequest || "valid AMP request"
        new AmpRequest()             || "invalid AMP request"
    }

    def "PBS should return proper error for unsupported types in AMP requests"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def ampStoredRequest = BidRequest.getDefaultBidRequest(channel)
        ampStoredRequest.setAccountId(ampRequest.account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: request.${channel.value.toLowerCase()} must not exist in AMP stored requests."

        where:
        channel << [DistributionChannel.APP, DistributionChannel.DOOH]
    }

    def "PBS should return info from the stored response when it's defined in the stored request"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with specified stored response"
        def storedResponseId = PBSUtils.randomNumber
        def ampStoredRequest = BidRequest.defaultStoredRequest
        ampStoredRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(ampStoredRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain information from stored response"
        def price = storedAuctionResponse.bid[0].price
        assert response.targeting["hb_pb"] == getRoundedTargetingValueWithDefaultPrecision(price)
        assert response.targeting["hb_size"] == "${storedAuctionResponse.bid[0].weight}x${storedAuctionResponse.bid[0].height}"

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(ampStoredRequest.id) == 0
    }

    def "PBS should prefer parameters from the request when stored request is specified"() {
        given: "AMP request"
        def msW = PBSUtils.randomNumber
        def msH = PBSUtils.randomNumber
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            curl = PBSUtils.randomString
            account = PBSUtils.randomNumber
            w = PBSUtils.randomNumber
            h = PBSUtils.randomNumber
            ms = "${msW}x${msH}"
            slot = PBSUtils.randomString
            gdprApplies = false
        }

        and: "Default stored request with specified: gdpr, debug"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        ampStoredRequest.regs.gdpr = 1

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampRequest.curl
        assert bidderRequest.site?.publisher?.id == ampRequest.account.toString()
        assert bidderRequest.imp[0]?.tagId == ampRequest.slot
        assert bidderRequest.imp[0]?.banner?.format*.height == [ampRequest.h, msH]
        assert bidderRequest.imp[0]?.banner?.format*.weight == [ampRequest.w, msW]
        assert bidderRequest.regs?.gdpr == (ampRequest.gdprApplies ? 1 : 0)
    }

    def "PBS should prefer ow,oh from the request when ads sizes specified in stored request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            ow = PBSUtils.randomNumber
            oh = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.imp[0]?.banner?.format*.height == [ampRequest.oh]
        assert bidderRequest.imp[0]?.banner?.format*.weight == [ampRequest.ow]
    }

    def "PBS should take parameters from the stored request when it's not specified in the request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampStoredRequest.site.page
        assert bidderRequest.site?.publisher?.id == ampStoredRequest.site.publisher.id
        assert !bidderRequest.imp[0]?.tagId
        assert bidderRequest.imp[0]?.banner?.format[0]?.height == ampStoredRequest.imp[0].banner.format[0].height
        assert bidderRequest.imp[0]?.banner?.format[0]?.weight == ampStoredRequest.imp[0].banner.format[0].weight
        assert bidderRequest.regs?.gdpr == ampStoredRequest.regs.gdpr
    }

    def "PBS should pass addtl_consent to user.ext.consented_providers_settings.consented_providers"() {
        given: "Default amp request with addtlConsent"
        def randomAddtlConsent = PBSUtils.randomString
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            addtlConsent = randomAddtlConsent
        }

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(
                    consentedProvidersSettingsKebabCase: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString),
                    consentedProvidersSettings: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)))
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain addtl consent"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.user.ext.consentedProvidersSettingsKebabCase.consentedProviders == randomAddtlConsent
        assert bidderRequest.user.ext.consentedProvidersSettings.consentedProviders == randomAddtlConsent
    }

    def "PBS should pass addtl_consent to user.ext.ConsentedProvidersSettings.consented_providers"() {
        given: "Default amp request with addtlConsent"
        def randomAddtlConsent = PBSUtils.randomString
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            addtlConsent = randomAddtlConsent
        }

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(
                    consentedProvidersSettingsKebabCase: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString),
                    consentedProvidersSettings: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)))
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain addtl consent"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.user.ext.consentedProvidersSettingsKebabCase.consentedProviders == randomAddtlConsent
        assert bidderRequest.user.ext.consentedProvidersSettings.consentedProviders == randomAddtlConsent
    }

    def "PBS shouldn't pass addtl_consent to user.ext.consented_providers_settings.consented_providers when addtl_consent not specified"() {
        given: "Default amp request without addtl_consent"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            addtlConsent = null
        }

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(
                    consentedProvidersSettingsKebabCase: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString),
                    consentedProvidersSettings: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)))
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain addtl consent"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequest.user.ext.consentedProvidersSettings.consentedProviders
        assert !bidderRequest.user.ext.consentedProvidersSettingsKebabCase.consentedProviders
    }

    def "PBS shouldn't pass addtl_consent to user.ext.ConsentedProvidersSettings.consented_providers when addtl_consent not specified"() {
        given: "Default amp request without addtl_consent"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            addtlConsent = null
        }

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(
                    consentedProvidersSettingsKebabCase: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString),
                    consentedProvidersSettings: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)))
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain addtl consent"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequest.user.ext.consentedProvidersSettings.consentedProviders
        assert !bidderRequest.user.ext.consentedProvidersSettingsKebabCase.consentedProviders
    }
}
