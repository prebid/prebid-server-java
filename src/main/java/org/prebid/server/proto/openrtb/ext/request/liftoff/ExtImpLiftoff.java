package org.prebid.server.proto.openrtb.ext.request.liftoff;

import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class ExtImpLiftoff {

    String bidToken;

    String appStoreId;

    String placementReferenceId;
}
