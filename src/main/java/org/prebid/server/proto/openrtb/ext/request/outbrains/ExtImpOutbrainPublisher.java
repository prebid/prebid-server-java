package org.prebid.server.proto.openrtb.ext.request.outbrains;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpOutbrainPublisher {

    String id;

    String name;

    String domain;
}
