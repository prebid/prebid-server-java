package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconImpExtRp {

    Integer zoneId;

    JsonNode target;

    RubiconImpExtRpTrack track;
}
