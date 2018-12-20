package org.prebid.server.bidder.model;

import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class ImpWithExt<T> {

    Imp imp;

    T impExt;
}
