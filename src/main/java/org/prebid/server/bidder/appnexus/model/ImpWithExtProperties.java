package org.prebid.server.bidder.appnexus.model;

import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ImpWithExtProperties {

    Imp imp;

    String memberId;

    Boolean generateAdPodId;
}
