package org.prebid.server.functional.model.db

import groovy.transform.ToString
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.typeconverter.AccountConfigTypeConverter
import org.prebid.server.functional.model.db.typeconverter.AccountStatusTypeConverter

import java.sql.Timestamp

import static javax.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "accounts_account")
@ToString(includeNames = true)
class Account {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "uuid")
    String uuid
    @Column(name = "price_granularity")
    String priceGranularity
    @Column(name = "banner_cache_ttl")
    Integer bannerCacheTtl
    @Column(name = "video_cache_ttl")
    Integer videoCacheTtl
    @Column(name = "events_enabled")
    Boolean eventsEnabled
    @Column(name = "tcf_config")
    String tcfConfig
    @Column(name = "truncate_target_attr")
    Integer truncateTargetAttr
    @Column(name = "default_integration")
    String defaultIntegration
    @Column(name = "analytics_config")
    String analyticsConfig
    @Column(name = "bid_validations")
    String bidValidations
    @Column(name = "status")
    @Convert(converter = AccountStatusTypeConverter)
    AccountStatus status
    @Column(name = "config")
    @Convert(converter = AccountConfigTypeConverter)
    AccountConfig config
    @Column(name = "updated_by")
    Integer updatedBy
    @Column(name = "updated_by_user")
    String updatedByUser
    @Column(name = "updated")
    Timestamp updated
}
