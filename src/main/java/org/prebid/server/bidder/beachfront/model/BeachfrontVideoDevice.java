package org.prebid.server.bidder.beachfront.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class BeachfrontVideoDevice {

    String ua;

    String ip;

    String js;
}
