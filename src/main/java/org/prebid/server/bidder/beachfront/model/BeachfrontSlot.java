package org.prebid.server.bidder.beachfront.model;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value(staticConstructor = "of")
public class BeachfrontSlot {

    String slot;

    String id;

    BigDecimal bidfloor;

    List<BeachfrontSize> sizes;
}
