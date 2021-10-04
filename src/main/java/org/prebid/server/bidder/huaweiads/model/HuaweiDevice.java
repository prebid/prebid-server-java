package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
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
    String gaidTrackingEnabled;
    String gaid;
    String clientTime;
    String ip;
}
