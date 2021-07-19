package org.prebid.server.bidder.pubmatic.model.response;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class VideoCreativeInfo {

    Integer duration;
}
