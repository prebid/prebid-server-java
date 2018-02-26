package org.prebid.server.proto.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public final class AmpResponse {

    Map<String, String> targeting;
}
