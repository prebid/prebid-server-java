package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class DebugWarning {

    int code;

    String message;
}
