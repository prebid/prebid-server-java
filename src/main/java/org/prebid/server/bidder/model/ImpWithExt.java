package org.prebid.server.bidder.model;

import com.iab.openrtb.request.Imp;
import lombok.Value;

@Value(staticConstructor = "of")
public class ImpWithExt<T> {

    Imp imp;

    T impExt;
}
