package org.prebid.server.functional.tests.storage

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED

class AccountS3Spec extends StorageBaseSpec {

    private final static Map<Stage, List<ModuleName>> S3_CONFIG = s3StorageConfig +
            mySqlDisabledConfig +
            ['settings.enforce-valid-account': 'true']

    private static final PrebidServerService s3StorageAccountPbsService = PbsServiceFactory.getService(S3_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(S3_CONFIG)
    }

    def "PBS should process request when active account is present in S3 storage"() {
        given: "Default BidRequest with account"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Active account config"
        def account = new AccountConfig(id: accountId, status: AccountStatus.ACTIVE)

        and: "Saved account in AWS S3 storage"
        s3Service.uploadAccount(DEFAULT_BUCKET, account)

        when: "PBS processes auction request"
        def response = s3StorageAccountPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatbid"
        assert response.seatbid.size() == 1
    }

    def "PBS should throw exception when inactive account is present in S3 storage"() {
        given: "Default BidRequest with account"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Inactive account config"
        def account = new AccountConfig(id: accountId, status: AccountStatus.INACTIVE)

        and: "Saved account in AWS S3 storage"
        s3Service.uploadAccount(DEFAULT_BUCKET, account)

        when: "PBS processes auction request"
        s3StorageAccountPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Account $accountId is inactive"
    }

    def "PBS should throw exception when account id isn't match with bid request account id"() {
        given: "Default BidRequest with account"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Account config with different accountId"
        def account = new AccountConfig(id: PBSUtils.randomString, status: AccountStatus.ACTIVE)

        and: "Saved account in AWS S3 storage"
        s3Service.uploadAccount(DEFAULT_BUCKET, account, accountId)

        when: "PBS processes auction request"
        s3StorageAccountPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS should throw exception when account is invalid in S3 storage json file"() {
        given: "Default BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Saved invalid account in AWS S3 storage"
        s3Service.uploadFile(DEFAULT_BUCKET, INVALID_FILE_BODY, "${S3Service.DEFAULT_ACCOUNT_DIR}/${accountId}.json")

        when: "PBS processes auction request"
        s3StorageAccountPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS should throw exception when account is not present in S3 storage and valid account enforced"() {
        given: "Default BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        when: "PBS processes auction request"
        s3StorageAccountPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: ${accountId}"
    }
}
