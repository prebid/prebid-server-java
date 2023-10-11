package org.prebid.server.bidder.audiencenetwork.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class AudienceNetworkExt {

    String platformid;

    String authenticationId;
}
