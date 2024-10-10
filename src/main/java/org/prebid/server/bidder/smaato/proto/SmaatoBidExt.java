package org.prebid.server.bidder.smaato.proto;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SmaatoBidExt {

    private static final SmaatoBidExt EMPTY = SmaatoBidExt.of(null, null);

    Integer duration;

    List<String> curls;

    public static SmaatoBidExt empty() {
        return EMPTY;
    }
}
