package org.prebid.server.functional.model.db

import groovy.transform.ToString
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.ImpConfigTypeConverter
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
    @Column(name = "uuid")
    String uuid
    @Column(name = "config")
    @Convert(converter = ImpConfigTypeConverter)
    Imp config
}
