package org.prebid.server.bidder.beachfront.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class BeachfrontBannerRequest {

    List<BeachfrontSlot> slots;

    String domain;

    String page;

    String referrer;

    String search;

    String secure;

    @JsonProperty("deviceOs")
    String deviceOs;

    @JsonProperty("deviceModel")
    String deviceModel;

    @JsonProperty("isMobile")
    Integer isMobile;

    String ua;

    Integer dnt;

    String user;

    @JsonProperty("adapterName")
    String adapterName;

    @JsonProperty("adapterVersion")
    String adapterVersion;

    String ip;
}
