package org.prebid.server.functional.model.db

import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.StoredRequestConfigTypeConverter
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest

import static jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "stored_requests")
@ToString(includeNames = true)
class StoredRequest {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "accountId")
    String accountId
    @Column(name = "reqId")
    String requestId
    @Column(name = "requestData")
    @Convert(converter = StoredRequestConfigTypeConverter)
    BidRequest requestData

    static StoredRequest getStoredRequest(AmpRequest ampRequest, BidRequest storedRequest) {
        getStoredRequest(ampRequest.account, ampRequest.tagId, storedRequest)
    }

    static StoredRequest getStoredRequest(BidRequest bidRequest) {
        getStoredRequest(getStoredRequestId(bidRequest), bidRequest)
    }

    static StoredRequest getStoredRequest(String storedRequestId, BidRequest bidRequest) {
        getStoredRequest(bidRequest.accountId, storedRequestId, bidRequest)
    }

    @Deprecated
    static StoredRequest getStoredRequest(BidRequest bidRequest, BidRequest storedRequest) {
        getStoredRequest(bidRequest.accountId, getStoredRequestId(bidRequest), storedRequest)
    }

    static StoredRequest getStoredRequest(String accountId, String storedRequestId, BidRequest bidRequest) {
        new StoredRequest().tap {
            it.accountId = accountId
            it.requestId = storedRequestId
            it.requestData = bidRequest
        }
    }

    private static String getStoredRequestId(BidRequest bidRequest) {
        def storedRequestId = bidRequest?.ext?.prebid?.storedRequest?.id

        if (!storedRequestId) {
            throw new IllegalStateException("Stored request id is missing")
        }

        storedRequestId
    }
}
