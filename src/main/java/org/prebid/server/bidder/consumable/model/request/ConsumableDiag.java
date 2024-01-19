package org.prebid.server.bidder.consumable.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ConsumableDiag {

    String pbsv;

    String pbjsv;

}
