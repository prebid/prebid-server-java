package org.prebid.server.bidder.sharethrough.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class UserInfo {

    String consent;

    String ttdUid;

    String stxuid;
}

