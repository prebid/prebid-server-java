package org.prebid.server.bidder.smaato.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SmaatoMediaData {

    String content;

    Integer w;

    Integer h;
}
