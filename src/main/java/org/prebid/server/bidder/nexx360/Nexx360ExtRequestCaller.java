package org.prebid.server.bidder.nexx360;

import lombok.Value;

@Value(staticConstructor = "of")
public class Nexx360ExtRequestCaller {

    String name;

    String version;

    public static Nexx360ExtRequestCaller of(String version) {
        return Nexx360ExtRequestCaller.of("Prebid-Server", version);
    }
}
