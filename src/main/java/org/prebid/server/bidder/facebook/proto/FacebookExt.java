package org.prebid.server.bidder.facebook.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class FacebookExt {

    String platformid;

    String authenticationId;
}
