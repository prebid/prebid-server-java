package org.prebid.server.bidder.smaato.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SmaatoImageAd {

    SmaatoImage image;
}
