package org.prebid.server.functional.model.db

import groovy.transform.ToString
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.StoredRequestConfigTypeConverter
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest

import static javax.persistence.GenerationType.IDENTITY

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
    @Column(name = "reqid")
    String reqid
    @Column(name = "requestData")
    @Convert(converter = StoredRequestConfigTypeConverter)
    BidRequest requestData

    static StoredRequest getDbStoredRequest(AmpRequest ampRequest, BidRequest bidRequest) {
        new StoredRequest(reqid: ampRequest.tagId, accountId: ampRequest.account, requestData: bidRequest)
    }
}
