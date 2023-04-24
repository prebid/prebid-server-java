package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

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

    Float pxratio;

    String imei;

    String oaid;

    String isTrackingEnabled;

    String emuiVer;

    String localeCountry;

    String belongCountry;

    String gaidTrackingEnabled;

    String gaid;

    String clientTime;

    String ip;

}
