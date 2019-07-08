package org.prebid.server.bidder.rubicon.proto;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Geo;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconUserExtRp {

    JsonNode target;

    String gender;

    Integer yob;

    Geo geo;
}
