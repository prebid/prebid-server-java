package org.prebid.server.bidder.beachfront.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class BeachfrontVideoRequest {

    @JsonProperty("isPrebid")
    Boolean isPrebid;

    @JsonProperty("appId")
    String appId;

    String id;

    List<BeachfrontVideoImp> imp;

    Site site;

    BeachfrontVideoDevice device;

    User user;

    List<String> cur;
}
