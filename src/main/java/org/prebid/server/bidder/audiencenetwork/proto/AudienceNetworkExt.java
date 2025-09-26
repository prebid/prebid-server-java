package org.prebid.server.bidder.audiencenetwork.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AudienceNetworkExt {

    @JsonProperty("platformid")
    String platformid;

    @JsonProperty("authentication_id")
    String authenticationId;

    @JsonProperty("security_app_id")
    String securityAppId;
}
