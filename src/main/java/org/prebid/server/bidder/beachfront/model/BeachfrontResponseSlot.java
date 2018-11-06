package org.prebid.server.bidder.beachfront.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BeachfrontResponseSlot {

    String crid;

    Float price;

    Integer w;

    Integer h;

    String slot;

    String adm;
}
