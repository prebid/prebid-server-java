package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class Device {

    @JsonProperty("type")
    Integer type;

    @JsonProperty("useragent")
    String userAgent;

    @JsonProperty("os")
    String os;

    @JsonProperty("version")
    String version;

    @JsonProperty("maker")
    String maker;

    @JsonProperty("model")
    String model;

    @JsonProperty("width")
    Integer width;

    @JsonProperty("height")
    Integer height;

    @JsonProperty("language")
    String language;

    @JsonProperty("buildVersion")
    String buildVersion;

    @JsonProperty("dpi")
    Integer dpi;

    @JsonProperty("pxratio")
    BigDecimal pxratio;

    @JsonProperty("imei")
    String imei;

    @JsonProperty("oaid")
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

    @JsonProperty("gaid")
    String gaid;

    @JsonProperty("clientTime")
    String clientTime;

    @JsonProperty("ip")
    String ip;
}
