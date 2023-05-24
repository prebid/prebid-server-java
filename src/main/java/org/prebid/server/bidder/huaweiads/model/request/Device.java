package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class Device {

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

    @JsonProperty("localeCountry")
    String localeCountry;

    @JsonProperty("belongCountry")
    String belongCountry;

    String gaidTrackingEnabled;

    String gaid;

    @JsonProperty("clientTime")
    String clientTime;

    String ip;
}
