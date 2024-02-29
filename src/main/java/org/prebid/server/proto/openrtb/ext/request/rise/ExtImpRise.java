package org.prebid.server.proto.openrtb.ext.request.rise;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpRise {

    String publisherId;

    String org;
}
