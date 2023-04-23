package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
