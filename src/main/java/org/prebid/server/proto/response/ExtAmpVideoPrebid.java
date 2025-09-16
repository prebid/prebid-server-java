package org.prebid.server.proto.response;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;

@Value(staticConstructor = "of")
public class ExtAmpVideoPrebid {

    ExtModules modules;
}
