package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED

class AccountSpec extends BaseSpec {

    def "PBS should reject request with inactive account"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account": enforceValidAccount as String])

        and: "Inactive account id"
        def accountId = PBSUtils.randomNumber
        def account = new Account(uuid: accountId, config: new AccountConfig(status: AccountStatus.INACTIVE))
        accountDao.save(account)

        and: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Account $accountId is inactive"

        where:
        enforceValidAccount << [true, false]
    }

    def "PBS should reject request with unknown account when settings.enforce-valid-account = true"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Non-existing account id"
        def accountId = PBSUtils.randomNumber

        and: "Default basic BidRequest with non-existing account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: $accountId"

        where:
        defaultAccountConfig << [null, AccountConfig.defaultAccountConfig]
    }

    def "PBS should reject request without account when settings.enforce-valid-account = true"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default basic BidRequest without account"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = null
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: "

        where:
        defaultAccountConfig << [null, AccountConfig.defaultAccountConfig]
    }

    def "PBS should not reject request with unknown account when settings.enforce-valid-account = false"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "false",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default basic BidRequest with non-existing account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not reject the entire auction"
        assert !response.seatbid?.isEmpty()

        where:
        defaultAccountConfig               || accountId
        null                               || null
        null                               || PBSUtils.randomNumber
        AccountConfig.defaultAccountConfig || null
        AccountConfig.defaultAccountConfig || PBSUtils.randomNumber
    }

    def "PBS AMP should reject request with unknown account when settings.enforce-valid-account = true"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default AMP request with non-existing account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = requestAccount
        }

        and: "Default stored request with non-existing account"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            site.publisher.id = storedRequestAccount
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        pbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        def resolvedAccount = requestAccount ?: storedRequestAccount
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: $resolvedAccount"

        where:
        defaultAccountConfig               || requestAccount        || storedRequestAccount
        null                               || PBSUtils.randomNumber || null
        null                               || null                  || PBSUtils.randomNumber
        AccountConfig.defaultAccountConfig || PBSUtils.randomNumber || null
        AccountConfig.defaultAccountConfig || null                  || PBSUtils.randomNumber
    }

    def "PBS AMP should reject request without account when settings.enforce-valid-account = true"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default AMP request without account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = null
        }

        and: "Default stored request without account"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            site.publisher.id = null
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        pbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: "

        where:
        defaultAccountConfig << [null, AccountConfig.defaultAccountConfig]
    }

    def "PBS AMP should not reject request with unknown account when settings.enforce-valid-account = false"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "false",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default AMP request with non-existing account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = requestAccount
        }

        and: "Default stored request with non-existing account"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            site.publisher.id = storedRequestAccount
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequest(ampRequest)

        then: "PBS should not reject request"
        assert response.targeting
        assert response.ext?.debug?.httpcalls

        where:
        defaultAccountConfig               || requestAccount        || storedRequestAccount
        null                               || PBSUtils.randomNumber || null
        null                               || null                  || PBSUtils.randomNumber
        AccountConfig.defaultAccountConfig || PBSUtils.randomNumber || null
        AccountConfig.defaultAccountConfig || null                  || PBSUtils.randomNumber
    }

    def "PBS AMP should not reject request without account when settings.enforce-valid-account = false"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "false",
                 "settings.default-account-config": mapper.encode(defaultAccountConfig)])

        and: "Default AMP request without account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = null
        }

        and: "Default stored request without account"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            site.publisher.id = null
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequest(ampRequest)

        then: "PBS should not reject request"
        assert response.targeting
        assert response.ext?.debug?.httpcalls

        where:
        defaultAccountConfig << [null, AccountConfig.defaultAccountConfig]
    }
}
