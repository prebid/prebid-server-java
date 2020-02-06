package org.prebid.server.bidder.smartrtb.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SmartrtbResponseExt {

    String format;
}
