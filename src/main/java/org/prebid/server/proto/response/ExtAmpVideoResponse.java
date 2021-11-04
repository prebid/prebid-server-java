package org.prebid.server.proto.response;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExtAmpVideoResponse {

    ExtResponseDebug debug;

    Map<String, List<ExtBidderError>> errors;

    ExtAmpVideoPrebid prebid;
}
