package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Value;

@Value(staticConstructor = "of")
public class KeywordSegment {

    String name;

    String value;
}

