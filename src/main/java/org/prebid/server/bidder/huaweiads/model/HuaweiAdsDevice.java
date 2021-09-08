package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Builder
@NoArgsConstructor
@Setter
@Getter
public class HuaweiAdsDevice {
    private Integer type;
    private String useragent;
    private String os;
    private String version;
    private String maker;
    private String model;
    private Integer width;
    private Integer height;
    private String language;
    private String buildVersion;
    private Integer dpi;
    private BigDecimal pxratio;
    private String imei;
    private String oaid;
    private String isTrackingEnabled;
    private String emuiVer;
    private String localeCountry;
    private String belongCountry;
    private String gaidTrackingEnabled;
    private String gaid;
    private String clientTime;
    private String ip;
}
