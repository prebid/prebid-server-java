package org.prebid.server.bidder.openweb.model;

import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ImpWithSourceId {

    Imp imp;

    Integer sourceId;
}
