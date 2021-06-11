package org.prebid.server.bidder.criteo.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class CriteoResponse {

    String id;

    List<CriteoResponseSlot> slots;
}
