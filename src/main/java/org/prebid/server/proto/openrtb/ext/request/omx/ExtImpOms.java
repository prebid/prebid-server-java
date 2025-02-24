package org.prebid.server.proto.openrtb.ext.request.omx;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOms {

    String pid;

    Integer publisherId;
}
