package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue

enum TargetingType {

    AD_UNIT_SIZE("adunit.size"),
    AD_UNIT_MEDIA_TYPE("adunit.mediatype"),
    AD_UNIT_AD_SLOT("adunit.adslot"),
    SITE_DOMAIN("site.domain"),
    SITE_PUBLISHER_DOMAIN("site.publisher.domain"),
    REFERRER("site.referrer"),
    APP_BUNDLE("app.bundle"),
    DEVICE_COUNTRY("device.geo.ext.netacuity.country"),
    DEVICE_TYPE("device.ext.deviceatlas.type"),
    DEVICE_OS("device.ext.deviceatlas.osfamily"),
    DEVICE_REGION("device.geo.ext.netacuity.region"),
    PAGE_POSITION("pos"),
    LOCATION("geo.distance"),
    BIDDER_PARAM("bidp."),
    USER_SEGMENT("segment."),
    USER_SEGMENT_NAME(USER_SEGMENT.value + "name"),
    UFPD("ufpd."),
    UFPD_LANGUAGE(UFPD.value + "language"),
    SFPD("sfpd."),
    SFPD_AMP(SFPD.value + "amp"),
    DOW("user.ext.time.userdow"),
    HOUR("user.ext.time.userhour"),
    INVALID("invalid.targeting.type")

    @JsonValue
    final String value

    private TargetingType(String value) {
        this.value = value
    }
}
