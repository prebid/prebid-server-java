package org.prebid.server.proto.openrtb.ext.request.lemmadigital;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLemmaDigital {

    int pid;

    int aid;
}
