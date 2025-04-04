package org.prebid.server.proto.openrtb.ext.request.outbrains;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpOutbrain {

    ExtImpOutbrainPublisher publisher;

    String tagid;

    List<String> bcat;

    List<String> badv;
}
