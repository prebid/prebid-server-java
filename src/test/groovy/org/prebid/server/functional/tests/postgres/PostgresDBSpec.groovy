package org.prebid.server.functional.tests.postgres

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils

class PostgresDBSpec extends PostgresBaseSpec {

    def "PBS with postgresql should proceed with stored requests and responses correctly"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with specified stored response"
        def storedResponseId = PBSUtils.randomNumber
        def ampStoredRequest = BidRequest.defaultStoredRequest
        ampStoredRequest.imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
        ampStoredRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Create and save account in the DB"
        def account = new Account(uuid: ampRequest.account)
        accountDao.save(account)

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(ampStoredRequest)
        storedImpDao.save(storedImp)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(ampStoredRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes amp request"
        def response = pbsServiceWithPostgres.sendAmpRequest(ampRequest)

        then: "PBS should not reject request"
        assert response.ext?.debug?.httpcalls
    }
}
