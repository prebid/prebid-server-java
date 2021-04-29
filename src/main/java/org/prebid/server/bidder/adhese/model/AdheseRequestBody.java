package org.prebid.server.bidder.adhese.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder
@Value
public class AdheseRequestBody {

    List<Slot> slots;
    Map<String, List<String>> parameters;

    @Builder
    @Value
    public static class Slot {

        String slotname;
    }
}
