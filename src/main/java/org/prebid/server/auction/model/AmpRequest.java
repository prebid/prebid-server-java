package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AmpRequest {

    String tagId;
}
