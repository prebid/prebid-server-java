package org.prebid.server.bidder.appnexus.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class AppnexusKeyVal {

    String key;

    List<String> value;
}
