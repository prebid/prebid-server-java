package org.prebid.server.proto.openrtb.ext.request.nextmillennium;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtRequestNextMillennium {

    List<String> nmmFlags;

    String nmVersion;

    String serverVersion;
}
