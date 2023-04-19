package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Device {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer type;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String useragent;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String os;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String version;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String maker;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String model;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer width;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer height;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String language;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String buildVersion;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer dpi;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Float pxratio;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String imei;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String oaid;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String isTrackingEnabled;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String emuiVer;

    String localeCountry;

    String belongCountry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String gaidTrackingEnabled;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String gaid;

    String clientTime;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String ip;

}
