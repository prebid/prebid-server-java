package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Cache {

    String url;

    String cacheHost;

    String cacheId;
}
