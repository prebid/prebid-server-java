package org.prebid.server.bidder.consumable.model;

import com.iab.openrtb.request.Eid;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor
@Value
public class ConsumableUser {

    String key;

    List<Eid> eids;
}
