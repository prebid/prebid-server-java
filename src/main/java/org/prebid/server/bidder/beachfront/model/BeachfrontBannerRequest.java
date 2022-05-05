package org.prebid.server.bidder.beachfront.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;

import java.util.List;

@Builder
@Value
public class BeachfrontBannerRequest {

    List<BeachfrontSlot> slots;

    String domain;

    String page;

    String referrer;

    String search;

    Integer secure;

    @JsonProperty("deviceOs")
    String deviceOs;

    @JsonProperty("deviceModel")
    String deviceModel;

    @JsonProperty("isMobile")
    Integer isMobile;

    String ua;

    Integer dnt;

    User user;

    @JsonProperty("adapterName")
    String adapterName;

    @JsonProperty("adapterVersion")
    String adapterVersion;

    String ip;

    @JsonProperty("requestId")
    String requestId;

    Boolean real204;

    ExtSourceSchain schain;
}
