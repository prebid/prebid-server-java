package org.prebid.server.bidder.thirtythreeacross.proto;

import lombok.NoArgsConstructor;
import lombok.Value;

@NoArgsConstructor(staticName = "of")
@Value
public class ThirtyThreeAcrossCaller {

    final String name = "Prebid-Server-Java";
    final String version = "N/A";
}
