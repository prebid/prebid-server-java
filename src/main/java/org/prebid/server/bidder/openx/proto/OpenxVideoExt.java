package org.prebid.server.bidder.openx.proto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
@Builder
public class OpenxVideoExt {

    Integer rewarded;
}
