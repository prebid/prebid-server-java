package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class Device {

    Integer type;

    @JsonProperty("useragent")
    String userAgent;

    String os;

    String version;

    String maker;

    String model;

    Integer width;

    Integer height;

    String language;

    @JsonProperty("buildVersion")
    String buildVersion;

    Integer dpi;

    BigDecimal pxratio;

    String imei;

    String oaid;

    @JsonProperty("isTrackingEnabled")
    String isTrackingEnabled;

    @JsonProperty("emuiVer")
    String emuiVer;

    @JsonProperty("localeCountry")
    String localeCountry;

    @JsonProperty("belongCountry")
    String belongCountry;

    @JsonProperty("gaidTrackingEnabled")
    String gaidTrackingEnabled;

    String gaid;

    @JsonProperty("clientTime")
    String clientTime;

    String ip;
}
