package org.prebid.server.proto.openrtb.ext.request.outbrains;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpOutbrain {

    ExtImpOutbrainPublisher publisher;

    String tagid;

    List<String> bcat;

    List<String> badv;
}
