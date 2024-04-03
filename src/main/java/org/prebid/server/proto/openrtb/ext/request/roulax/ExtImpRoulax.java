package org.prebid.server.proto.openrtb.ext.request.roulax;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpRoulax {

    String publisherPath;

    String pid;
}
