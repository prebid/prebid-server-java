package org.prebid.server.proto.openrtb.ext.request.mediasquare;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMediasquare {

    String owner;

    String code;
}
