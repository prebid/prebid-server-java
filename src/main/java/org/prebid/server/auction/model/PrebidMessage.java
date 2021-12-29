package org.prebid.server.auction.model;

import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class PrebidMessage {

    Set<String> tags;

    String message;
}
