package org.prebid.server.proto.openrtb.ext.request.visx;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpVisx {

    Integer uid;

    List<Integer> size;
}
