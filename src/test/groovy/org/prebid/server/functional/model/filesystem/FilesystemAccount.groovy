package org.prebid.server.functional.model.filesystem

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig


import java.sql.Timestamp


@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class FilesystemAccount {

    Integer id
    String priceGranularity
    Integer bannerCacheTtl
    Integer videoCacheTtl
    Boolean eventsEnabled
    String tcfConfig
    Integer truncateTargetAttr
    String defaultIntegration
    String analyticsConfig
    String bidValidations
    AccountStatus status
    AccountConfig config
    Integer updatedBy
    String updatedByUser
    Timestamp updated
}
