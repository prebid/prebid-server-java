package org.prebid.server.proto.openrtb.ext.request.rubicon;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconImpExtContext {

    ObjectNode data;
}
