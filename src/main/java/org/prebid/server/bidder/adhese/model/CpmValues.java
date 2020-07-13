package org.prebid.server.bidder.adhese.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class CpmValues {

    String amount;

    String currency;
}
