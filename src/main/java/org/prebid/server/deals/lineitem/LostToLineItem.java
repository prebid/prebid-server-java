package org.prebid.server.deals.lineitem;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.concurrent.atomic.LongAdder;

@AllArgsConstructor(staticName = "of")
@Value
public class LostToLineItem {

    String lineItemId;

    LongAdder count;
}
