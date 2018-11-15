package org.prebid.server.bidder.beachfront.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class BeachfrontSlot {

    String slot;

    String id;

    BigDecimal bidfloor;

    List<BeachfrontSize> sizes;
}
