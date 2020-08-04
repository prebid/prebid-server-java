package org.prebid.server.proto.openrtb.ext.request.ucfunnel;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpUcfunnel {

    String adunitid;

    String partnerid;
}
