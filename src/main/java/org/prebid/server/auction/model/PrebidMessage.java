package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class PrebidMessage {

    int code;

    String message;
}
