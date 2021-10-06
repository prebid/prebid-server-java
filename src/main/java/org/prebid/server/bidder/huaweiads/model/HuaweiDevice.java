package org.prebid.server.bidder.huaweiads.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value(staticConstructor = "of")
public class HuaweiDevice {

    Integer type;

    String useragent;

    String os;

    String version;

    String maker;

    String model;

    Integer width;

    Integer height;

    String language;

    String buildVersion;

    Integer dpi;

    BigDecimal pxratio;

    String imei;

    String oaid;

    String isTrackingEnabled;

    String emuiVer;

    String localeCountry;

    String belongCountry;

    @JsonProperty("gaidTrackingEnabled")
    String isGaidTrackingEnabled;

    String gaid;

    String clientTime;

    String ip;
}

