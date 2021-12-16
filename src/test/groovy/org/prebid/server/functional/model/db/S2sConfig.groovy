package org.prebid.server.functional.model.db

import groovy.transform.ToString
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

import static javax.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "s2sconfig_config")
@ToString(includeNames = true)
class S2sConfig {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "uuid", nullable = false)
    String uuid
    @Column(name = "config")
    String config
}
