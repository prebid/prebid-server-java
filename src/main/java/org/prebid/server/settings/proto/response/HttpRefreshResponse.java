package org.prebid.server.settings.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class HttpRefreshResponse {

    Map<String, ObjectNode> requests;

    Map<String, ObjectNode> imps;
}
