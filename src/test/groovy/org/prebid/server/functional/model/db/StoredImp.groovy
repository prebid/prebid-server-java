package org.prebid.server.functional.model.db

import groovy.transform.ToString
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.ImpConfigTypeConverter
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp

import static javax.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "stored_imps")
@ToString(includeNames = true)
class StoredImp {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "accountId")
    String accountId
    @Column(name = "impid")
    String impReqId
    @Column(name = "impData")
    @Convert(converter = ImpConfigTypeConverter)
    Imp impData

    static StoredImp getDbStoredImp(BidRequest bidRequest, Imp storedImp) {
        new StoredImp().tap {
            accountId = bidRequest?.site?.publisher?.id
            impReqId = bidRequest?.imp[0]?.ext?.prebid?.storedRequest?.id
            impData = storedImp
        }
    }
}
