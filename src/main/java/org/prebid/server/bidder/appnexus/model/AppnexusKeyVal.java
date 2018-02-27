package org.prebid.server.bidder.appnexus.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class AppnexusKeyVal {

    String key;

    List<String> value;
}
