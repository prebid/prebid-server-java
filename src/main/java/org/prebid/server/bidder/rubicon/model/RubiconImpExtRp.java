package org.prebid.server.bidder.rubicon.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconImpExtRp {

    Integer zoneId;

    JsonNode target;

    RubiconImpExtRpTrack track;
}
