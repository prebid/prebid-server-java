package org.prebid.server.bidder.rubicon.proto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconSiteExtRp {

    Integer siteId;

    JsonNode target;
}
