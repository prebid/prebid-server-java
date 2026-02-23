package org.prebid.server.proto.openrtb.ext.request.outbrains;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOutbrainPublisher {

    String id;

    String name;

    String domain;
}
