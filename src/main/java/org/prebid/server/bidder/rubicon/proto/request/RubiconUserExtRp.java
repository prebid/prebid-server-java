package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconUserExtRp {

    JsonNode target;
}
