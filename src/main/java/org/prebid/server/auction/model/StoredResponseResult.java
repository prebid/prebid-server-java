package org.prebid.server.auction.model;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.SeatBid;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class StoredResponseResult {

    List<Imp> requiredRequestImps;

    List<SeatBid> storedResponse;
}
