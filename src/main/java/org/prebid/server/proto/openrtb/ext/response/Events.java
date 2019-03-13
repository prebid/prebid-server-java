package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Events {

    String win;

    String view;
}
