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
    DEVICE_METRO("device.geo.ext.netacuity.metro"),
    PAGE_POSITION("pos"),
    LOCATION("geo.distance"),
    BIDP("bidp."),
    BIDP_ACCOUNT_ID(BIDP.value + "rubicon.accountId"),
    USER_SEGMENT("segment."),
    USER_SEGMENT_NAME(USER_SEGMENT.value + "name"),
    UFPD("ufpd."),
    UFPD_LANGUAGE(UFPD.value + "language"),
    UFPD_KEYWORDS(UFPD.value + "keywords"),
    UFPD_BUYER_ID(UFPD.value + "buyerid"),
    UFPD_BUYER_IDS(UFPD.value + "buyerids"),
    SFPD("sfpd."),
    SFPD_AMP(SFPD.value + "amp"),
    SFPD_LANGUAGE(SFPD.value + "language"),
    SFPD_KEYWORDS(SFPD.value + "keywords"),
    SFPD_BUYER_ID(SFPD.value + "buyerid"),
    SFPD_BUYER_IDS(SFPD.value + "buyerids"),
    DOW("user.ext.time.userdow"),
    HOUR("user.ext.time.userhour"),
    INVALID("invalid.targeting.type")

    @JsonValue
    final String value

    private TargetingType(String value) {
        this.value = value
    }
}
