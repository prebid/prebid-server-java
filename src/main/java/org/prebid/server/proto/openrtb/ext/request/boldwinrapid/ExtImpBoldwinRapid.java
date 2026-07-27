package org.prebid.server.proto.openrtb.ext.request.boldwinrapid;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBoldwinRapid {

    String pid;

    String tid;
}
