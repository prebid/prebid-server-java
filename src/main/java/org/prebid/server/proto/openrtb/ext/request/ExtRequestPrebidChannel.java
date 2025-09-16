package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestPrebidChannel {

    String name;

    String version;

    public static ExtRequestPrebidChannel of(String name) {
        return of(name, null);
    }
}
