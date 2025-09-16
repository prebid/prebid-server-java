package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class IpAddress {

    String ip;

    IP version;

    public enum IP {
        v4, v6
    }
}
