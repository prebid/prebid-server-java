package org.prebid.server.bidder.pubmatic.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticExtAdServer {

    String name;

    String adslot;
}
