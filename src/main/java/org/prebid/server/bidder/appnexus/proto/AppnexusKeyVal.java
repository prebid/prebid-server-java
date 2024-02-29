package org.prebid.server.bidder.appnexus.proto;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AppnexusKeyVal {

    String key;

    List<String> value;
}
