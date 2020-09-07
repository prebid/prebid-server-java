package org.prebid.server.bidder.rubicon.proto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconUserExtRp {

    JsonNode target;
}
