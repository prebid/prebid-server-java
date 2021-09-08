package org.prebid.server.deals.proto.report;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.concurrent.atomic.LongAdder;

@AllArgsConstructor(staticName = "of")
@Value
public class Event {

    String type;

    LongAdder count;
}
