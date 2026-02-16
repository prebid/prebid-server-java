package org.prebid.server.proto.openrtb.ext.request.rediads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpRediads {

    String accountId;

    String slot;

    String endpoint;
}
