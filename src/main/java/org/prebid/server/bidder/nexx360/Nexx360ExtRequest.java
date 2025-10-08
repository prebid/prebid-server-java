package org.prebid.server.bidder.nexx360;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value(staticConstructor = "of")
public class Nexx360ExtRequest {

    List<Nexx360ExtRequestCaller> caller;

    public static Nexx360ExtRequest of(Nexx360ExtRequestCaller caller) {
        return of(Collections.singletonList(caller));
    }
}
