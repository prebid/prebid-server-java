package org.prebid.server.bidder.facebook.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NormalizedFacebookParams {

    String placementId;

    String pubId;
}
