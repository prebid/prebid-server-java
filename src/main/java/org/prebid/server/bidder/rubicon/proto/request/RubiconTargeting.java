package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RubiconTargeting {

    String key;

    List<String> values;
}
